package top.lizhistudio.luajni.test

import top.lizhistudio.annotation.LuaFunction

object SimpleFunction {
  @LuaFunction
  fun add(a: Int, b: Int): Int {
    return a + b
  }
  @LuaFunction
  fun sub(a: Int, b: Int): Int {
    return a - b
  }
}

@LuaFunction
fun testName(n:String):String{
  return "Hello $n"
}