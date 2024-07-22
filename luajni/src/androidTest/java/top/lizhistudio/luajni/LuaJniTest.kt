package top.lizhistudio.luajni

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import top.lizhistudio.luajni.core.LuaError

import top.lizhistudio.luajni.core.LuaInterpreter
import top.lizhistudio.luajni.test.CompanionObjectFunction
import top.lizhistudio.luajni.test.InsideClass
import top.lizhistudio.luajni.test.SimpleEnumJava
import top.lizhistudio.luajni.test.SimpleEnvironment
import top.lizhistudio.luajni.test.SimpleFunction
import top.lizhistudio.luajni.test.SimpleTest
import top.lizhistudio.luajni.test.WrapperTest


@RunWith(AndroidJUnit4::class)
class LuaJniTest {

  @Test
  fun testException(){
    val lua = LuaInterpreter()
    try {
      lua.execute("return 1 +")
      fail("Should throw exception")
    } catch (e: Exception) {
      assertTrue(e is LuaError)
    }
    try{
      lua.execute("assert(false)")
      fail("Should throw exception")
    }catch (e: Exception){
      assertTrue(e is LuaError)
    }
    lua.destroy()
  }

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
    val obj = SimpleTest()
    var code = """
      assert(SimpleTest.oneValue == ${obj.oneValue})
      assert(SimpleTest.twoValue == ${obj.twoValue})
      assert(SimpleTest:add() == ${obj.add()})
    """.trimIndent()
    lua.execute(code)
    obj.oneValue = 10
    obj.twoValue = 20
    code = """
      SimpleTest.oneValue = 10
      SimpleTest.twoValue = 20
      assert(SimpleTest.oneValue == ${obj.oneValue})
      assert(SimpleTest.twoValue == ${obj.twoValue})
      assert(SimpleTest:add() == ${obj.add()})
    """.trimIndent()
    lua.execute(code)
    obj.setValue(55)
    code ="""
      SimpleTest:setValue(55)
      assert(SimpleTest.oneValue == ${obj.oneValue})
      assert(SimpleTest.twoValue == ${obj.twoValue})
      assert(SimpleTest:add() == ${obj.add()})
    """.trimIndent()
    lua.execute(code)
    obj.setValue(100, 200)
    code ="""
      SimpleTest:setValue(100, 200)
      assert(SimpleTest.oneValue == ${obj.oneValue})
      assert(SimpleTest.twoValue == ${obj.twoValue})
      assert(SimpleTest:add() == ${obj.add()})
    """.trimIndent()
    lua.execute(code)
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
    lua.register(SimpleEnumJava::class.java)
    val code = """
      assert(SimpleEnumByJava.A == ${SimpleEnumJava.A})
      assert(SimpleEnumByJava.B == ${SimpleEnumJava.B})
    """.trimIndent()
    lua.execute(code)
    lua.destroy()
  }


  @Test
  fun testSimpleFunction(){
    val lua = LuaInterpreter()
    lua.register(SimpleFunction::class.java)
    val code = """
      assert(add(1,2) == ${SimpleFunction.add(1,2)})
      assert(sub(10,2) == ${SimpleFunction.sub(10,2)})
    """.trimIndent()
    lua.execute(code)
    lua.destroy()
  }

  @Test
  fun testCompanionObjectFunction(){
    val lua = LuaInterpreter()
    lua.register(CompanionObjectFunction::class.java)
    val code = """
      assert(add(1,2) == ${CompanionObjectFunction.add(1,2)})
      assert(sub(10,2) == ${CompanionObjectFunction.sub(10,2)})
    """.trimIndent()
    lua.execute(code)
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
    val obj = InsideClass.InnerClass()
    lua.execute("""
      local obj = InnerClass:test()
      assert(obj:add() == ${obj.test().add()})
    """.trimIndent())
    lua.destroy()
  }

  @Test
  fun testWrapperTest(){
    val lua = LuaInterpreter()
    lua.register(
      SimpleTest::class.java,
      WrapperTest::class.java)
    val obj = WrapperTest("Hello")
    val code = """
      local obj = WrapperTest("Hello")
      assert(obj:test(1) == ${obj.test(1)})
      assert(obj:test(nil) == ${obj.test(null)})
      assert(obj.name == "${obj.name}")
      assert(obj.value == ${obj.value})
      assert(obj.simpleTest.oneValue == ${obj.simpleTest.oneValue})
      assert(obj.simpleTest.twoValue == ${obj.simpleTest.twoValue})
      assert(obj.simpleTest:add() == ${obj.simpleTest.add()})
    """.trimIndent()
    lua.execute(code)
    lua.destroy()
  }

  @Test
  fun testSimpleEnvironment() {
    val lua = LuaInterpreter()
    lua.register(SimpleEnvironment::class.java)
    val code = """
      assert(NAME == "${SimpleEnvironment.NAME}")
      assert(AGE == ${SimpleEnvironment.AGE})
      assert(MAN == ${SimpleEnvironment.MAN})
    """.trimIndent()
    lua.execute(code)
    lua.destroy()
  }
}