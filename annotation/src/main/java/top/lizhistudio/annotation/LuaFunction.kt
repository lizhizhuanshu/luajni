package top.lizhistudio.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaFunction(
  val alias: String = "",
  val unpack:Array<String> = []
)

