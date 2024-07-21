package top.lizhistudio.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaEnum(
  val alias: String = ""
)
