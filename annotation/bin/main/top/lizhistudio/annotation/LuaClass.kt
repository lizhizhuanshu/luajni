package top.lizhistudio.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaClass(
  val alias: String = "",
  val autoRegister: Boolean = false
)
