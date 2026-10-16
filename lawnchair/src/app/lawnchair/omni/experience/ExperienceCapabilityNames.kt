package app.lawnchair.omni.experience

object ExperienceCapabilityNames {
    val read = setOf("launcher.get_layout", "launcher.list_actions", "launcher.list_scenes", "launcher.list_folders", "launcher.shelf.list", "launcher.widget_stacks.list", "launcher.oem.status")
    val control = setOf("launcher.move_items", "launcher.set_grid", "launcher.create_folder", "launcher.open_action", "launcher.save_scene", "launcher.apply_scene", "launcher.set_folder_rule", "launcher.set_alias", "launcher.shelf.publish", "launcher.widget_stacks.select")
    val oem = setOf("launcher.oem.suspend_app")
}
