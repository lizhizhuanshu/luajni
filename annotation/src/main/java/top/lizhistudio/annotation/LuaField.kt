package top.lizhistudio.annotation

@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION,AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaField(
  val alias: String = "",
  val readonly: Boolean = false,
  val method2field: Boolean = false
)
