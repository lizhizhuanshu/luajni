package top.lizhistudio.luajni.test

import top.lizhistudio.annotation.LuaClass
import top.lizhistudio.annotation.LuaField


@LuaClass(autoRegister = true)
class SimpleTest {
  @LuaField
  var oneValue = 1
  @LuaField
  var twoValue = 2

  @LuaField
  fun add():Int{
    return oneValue + twoValue
  }

  @LuaField
  fun setOne(value:Int){
    oneValue = value
  }

  @LuaField
  fun setTwo(value:Int){
    twoValue = value
  }

  @LuaField
  fun setValue(value:Int){
    oneValue = value
  }

  @LuaField
  fun setValue(value:Int, value2:Int){
    oneValue = value
    twoValue = value2
  }
}


