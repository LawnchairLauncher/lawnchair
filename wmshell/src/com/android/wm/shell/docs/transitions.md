# Shell transitions
[Back to home](README.md)

---

## General

General guides for using Shell Transitions can be found here:
- [Shell transitions animation guide](http://go/shell-transit-anim)
- [Hitchhiker's guide to transitions](http://go/transitions-book)

## Transient-launch transitions
<span style="color:orange">Use with care!</span>

Transient-launch transitions are a way to handle non-atomic (ie. gestural) transitions by allowing
WM Core to put participating activities into a transiently visible or hidden state for the duration
of the animation and adding the ability to cancel the transition.  

For example, if you are launching an activity normally, WM Core will be updated
at the start of the animation which includes pausing the previous activity and resuming the next
activity (and subsequently the transition will reconcile that state via an animation).

If you are transiently launching an activity though, WM Core will ensure that both the leaving 
activity and the incoming activity will be RESUMED for the duration of the transition duration. In
addition, WM Core will track the position of the transient-launch activity in the window hierarchy
prior to the launch, and allow Shell to restore it to that position if the transitions needs to be
canceled.

Starting a transient-launch transition can be done via the activity options (since the activity may
not have been started yet):
```kotlin
val opts = ActivityOptions.makeBasic().setTransientLaunch()
val wct = WindowContainerTransaction()
wct.sendPendingIntent(pendingIntent, new Intent(), opts.toBundle())
transitions.startTransition(TRANSIT_OPEN, wct, ...)
```

And restoring the transient order via a WCT:
```kotlin
val wct = WindowContainerTransaction()
wct.restoreTransientOrder(transientLaunchContainerToken)
transitions.startTransition(TRANSIT_RESTORE, wct, ...)
```

### <span style="color:orange">Considerations</span>

Usage of transient-launch transitions should be done with consideration, there are a few gotchas
that might result in subtle and hard-to-reproduce issues. 

#### Understanding the flow
When starting a transient-launch transition, there are several possible outcomes:
1) The transition finishes as normal: The user is committing the transition to the state requested
   at the start of the transition.  In such cases, you can simply finish the transition and the
   states of the transiently shown/hidden activities will be updated to match the original state
   that a non-transient transition would have (ie. closing activities will be stopped).

2) The transition is interrupted: A change in the system results in the window hierarchy changing
   in a way which may or may not affect the transient-launch activity.  eg. We transiently-launch
   home from app A, but then app B launches.  In this case, WM attempts to create a new transition
   reflecting the window hierarchy changes (ie. if B occludes Home in the above example, then the
   transition will have Home TO_BACK, and B TO_FRONT).

   At this point, the transition handler can choose to merge the incoming transition or not (to
   queue it after this current transition).  Take note of the next section for concerns re. bookend
   transitions.

3) The transition is canceled: The user is canceling the transition to the previous state.  In such
   cases, you need to store the `WindowContainerToken` for the task associated with the 
   transient-launch activity, and restore the transient order via the `WindowContainerTransaction`
   API above.  In some cases, if anything has been reordered since (ie. due to other merged 
   transitions), then you may also want to use `WindowContainerTransaction#reorder()` to place all
   the relevant containers to their original order (provided via the change-order in the initial
   launch transition).

#### Finishing the transient-launch transition
When restoring the transient order in the 3rd flow above, it is recommended to do it in a new 
transition and <span style="color:orange">**not**</span> via the WindowContainerTransaction in 
`TransitionFinishCallback#onTransitionFinished()` provided when starting the transition.

Changes to the window hierarchy via the finish transaction are not applied in sync with other 
transitions that are collecting and applying, and are also not observable in Shell in any way.
Starting a new transition instead ensures both.  (The finish transaction can still be used if there
are non-transition affecting properties (ie. container properties) that need to be updated as a part
of finishing the transient-launch transition).

So the general idea is when restoring is:

1) Start transient-launch transition START_T
2) ...
3) All done, start bookend transition END_T
4) Handler receives END_T, merges it and then finishes START_T

In practice it's not quite that simple, due to the ordering of transitions and extra care must be
taken when using a new transition to prevent deadlocking when merging transitions.

When a new transition arrives while a transient-launch transition is playing, the handler can
choose to handle/merge the transition into the ongoing one, or skip merging to queue it up to be
played after.  In the above flow, we can see how this might result in a deadlock:

Queueing END during merge:
1) Start transient-launch transition START_T
2) ...
3) Incoming transition OTHER_T, choose to cancel START_T -> start bookend transition END_T, but don't merge OTHER_T
3) Waiting for END_T... <span style="color:red">Deadlock!</span>

Interrupt while pending END:
1) Start transient-launch transition START_T
2) ...
3) All done, start bookend transition END_T
3) Incoming transition OTHER_T occurs before END_T, but don't merge OTHER_T
3) Waiting for END_T... <span style="color:red">Deadlock!</span>

This means that when using transient-launch transitions with a bookend transition
<span style="color:orange">requires</span> you to handle any incoming transitions if the bookend is 
ever queued (or already posted) after it.  You can do so by preempting the bookend transition
(finishing the transient-launch transition), or handling the merge of the new transition (so it 
doesn't queue).

#### Finish-transactions with bookend transitions
Another thing to keep in mind when using bookend transitions is the ordering of the finish \
transactions.  In particular, if cleanup of tasks is necessary, then you need to ensure that the
cleanup happens on the bookend's finish transaction and not the finish transaction provided when
starting the animation, otherwise properties on the tasks in the transition could be overwritten by
the default finish transaction created by Core (see `Transition#buildFinishTransaction()`).

ie.
1) Start START (Core builds finishT1 to reset state)
2) Start BOOKEND (Core builds finishT2 to reset state)
3) Merge BOOKEND into START
4) <<< Finish transaction properties should be updated here and not before 2)
5) Finish BOOKEND (Shell transitions applies finishT1 then finishT2 in order)
