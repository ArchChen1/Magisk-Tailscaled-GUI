package top.cenmin.tailcontrol.core.model

enum class ConflictBehavior(val cliValue: String) {
    Rename("rename"),
    Skip("skip"),
    Overwrite("overwrite");

    companion object {
        fun fromCli(s: String?): ConflictBehavior =
            entries.firstOrNull { it.cliValue == s } ?: Rename
    }
}

data class DropConfig(
    val enabled: Boolean = false,
    val path: String = "/sdcard/Download/TailDrop/",
    val conflict: ConflictBehavior = ConflictBehavior.Rename,
)
