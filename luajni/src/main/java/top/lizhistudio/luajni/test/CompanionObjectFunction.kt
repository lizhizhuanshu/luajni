package top.lizhistudio.luajni.test

import top.lizhistudio.annotation.LuaFunction

class CompanionObjectFunction {
  companion object {

    @LuaFunction
    @JvmStatic
    fun add(a: Int, b: Int): Int {
      return a + b
    }

    @LuaFunction
    @JvmStatic
    fun sub(a: Int, b: Int): Int {
      return a - b
    }
  }
}