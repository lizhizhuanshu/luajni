package top.lizhistudio.luajni

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

import top.lizhistudio.luajni.core.LuaInterpreter
import top.lizhistudio.luajni.test.SimpleTest


@RunWith(AndroidJUnit4::class)
class LuaJniTest {

  @Test
  fun simpleTest(){
    val lua = LuaInterpreter()
    val result = lua.execute("return 1 + 1")
    assertEquals(2L, result)
    lua.destroy()
  }


  @Test
  fun simpleObjectTest(){
    val lua = LuaInterpreter()
    lua.register(SimpleTest::class.java)
    val r1 = lua.execute("return SimpleTest.oneValue")
    assertEquals(1L, r1)
    val r2 = lua.execute("return SimpleTest.twoValue")
    assertEquals(2L, r2)
    val r3 = lua.execute("return SimpleTest:add()")
    assertEquals(3L, r3)
    val r4 = lua.execute("""
      SimpleTest.oneValue = 10
      return SimpleTest.oneValue
    """.trimIndent())
    assertEquals(10L, r4)
    val r5 = lua.execute("""
      SimpleTest:setTwo(20)
      return SimpleTest.twoValue
    """.trimIndent())
    assertEquals(20L, r5)
    lua.destroy()
  }



}