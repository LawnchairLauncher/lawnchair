###ViewCapture Library Readme

ViewCapture.java is extremely performance sensitive. Any changes should be carried out with great caution not to hurt performance.

The following measurements should serve as a performance baseline (as of 02.10.2022):


The onDraw() function invocation time in WindowListener within ViewCapture is measured with System.nanoTime(). The following scenario was measured:

1. Capturing the notification shade window root view on a freshly rebooted bluejay device (2 notifications present) -> avg. time = 204237ns (0.2ms)

