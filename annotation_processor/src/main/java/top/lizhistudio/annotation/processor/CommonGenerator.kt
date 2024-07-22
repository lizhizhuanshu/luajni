package top.lizhistudio.annotation.processor

abstract class CommonGenerator:Generator {
  override fun headerCode():String{
    return GenerateUtil.headerCode(this)
  }
  override fun fileName():String{
    return className().replace(".","_").replace("$","__")
  }
  override fun injectToLuaMethodName():String{
    return "inject_${fileName()}"
  }
}

