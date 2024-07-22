package top.lizhistudio.luajni.test

import top.lizhistudio.annotation.LuaClass
import top.lizhistudio.annotation.LuaField

@LuaClass()
class  WrapperTest @LuaField constructor(@LuaField var name: String) {
  @LuaField constructor(v:Int):this("default"){
    this.value = v
  }
  @LuaField
  var value = 0
  @LuaField
  val simpleTest:SimpleTest = SimpleTest()
  @LuaField
  fun test(a:Int?):Int?{
    return a?.let { it + 1 }
  }
}