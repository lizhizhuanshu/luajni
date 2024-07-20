package top.lizhistudio.annotation.processor.data



data class GeneratorContext(val needRelease:MutableList<String> = mutableListOf()){
  constructor(s:GeneratorContext):this(s.needRelease.toMutableList())
  fun clone():GeneratorContext{
    return GeneratorContext(this)
  }
}
