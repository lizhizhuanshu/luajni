package top.lizhistudio.luajni.test

import top.lizhistudio.annotation.LuaEnvironment

@LuaEnvironment
object SimpleEnvironment {
  const val NAME = "li zhi"
  const val AGE = 18
  const val MAN = true
  var TEST = 11
}