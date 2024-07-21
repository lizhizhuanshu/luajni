package top.lizhistudio.luajni

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

import top.lizhistudio.luajni.core.LuaInterpreter
import top.lizhistudio.luajni.test.InsideClass
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

  @Test
  fun testKotlinSimpleEnum(){
    val lua = LuaInterpreter()
    lua.register(top.lizhistudio.luajni.test.SimpleEnum::class.java)
    val r1 = lua.execute("return SimpleEnum.A")
    assertEquals(1L, r1)
    val r2 = lua.execute("return SimpleEnum.B")
    assertEquals(30L, r2)
    lua.destroy()
  }

  @Test
  fun testJavaSimpleEnum(){
    val lua = LuaInterpreter()
    lua.register(top.lizhistudio.luajni.test.SimpleEnumJava::class.java)
    val r1 = lua.execute("return SimpleEnumByJava.A")
    assertEquals(1L, r1)
    val r2 = lua.execute("return SimpleEnumByJava.B")
    assertEquals(30L, r2)
    lua.destroy()
  }


  @Test
  fun testSimpleFunction(){
    val lua = LuaInterpreter()
    lua.register(top.lizhistudio.luajni.test.SimpleFunction::class.java)
    val r1 = lua.execute("return add(1,2)")
    assertEquals(3L, r1)
    val r2 = lua.execute("return sub(10,2)")
    assertEquals(8L, r2)
    lua.destroy()
  }

  @Test
  fun testCompanionObjectFunction(){
    val lua = LuaInterpreter()
    lua.register(top.lizhistudio.luajni.test.CompanionObjectFunction::class.java)
    val r1 = lua.execute("return add(1,2)")
    assertEquals(3L, r1)
    val r2 = lua.execute("return sub(10,2)")
    assertEquals(8L, r2)
    lua.destroy()
  }

  @Test
  fun testPackageFunction(){
    val lua = LuaInterpreter()
    lua.register("top.lizhistudio.luajni.test.SimpleFunctionKt")
    val r1 = lua.execute("return testName('World')")
    assertEquals("Hello World", r1)
    lua.destroy()
  }

  @Test
  fun testInsideClass(){
    val lua = LuaInterpreter()
    lua.register(SimpleTest::class.java,
      InsideClass.InnerClass::class.java)
    val r1 = lua.execute("""
      local obj = InnerClass:test()
      return obj:add()
    """.trimIndent())
    assertEquals(3L, r1)
  }

}