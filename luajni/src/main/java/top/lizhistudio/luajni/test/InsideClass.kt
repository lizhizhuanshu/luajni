package top.lizhistudio.luajni.test

import top.lizhistudio.annotation.LuaClass
import top.lizhistudio.annotation.LuaField

class InsideClass {
  @LuaClass(autoRegister = true)
  class InnerClass {
    @LuaField
    fun test():SimpleTest {
      return SimpleTest()
    }

    @LuaField(unpack = ["oneValue", "twoValue"])
    fun unpack():SimpleTest {
      return SimpleTest()
    }
  }
}