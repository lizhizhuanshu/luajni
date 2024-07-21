package top.lizhistudio.annotation.processor


import top.lizhistudio.annotation.LuaEnum
import top.lizhistudio.annotation.processor.GenerateUtil.getJvmName
import top.lizhistudio.annotation.processor.GenerateUtil.mIndent
import top.lizhistudio.annotation.processor.GenerateUtil.shortName
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement


class EnumCodeGenerator(private val clazz: TypeElement):Generator {
  data class EnumField(val name: String, val value: Int)
  private val fields = mutableListOf<EnumField>()
  init {
    clazz.enclosedElements.filter {
      it is VariableElement && it.kind == ElementKind.FIELD && it.constantValue !=null
    }.forEach {
      it as VariableElement
      fields.add(EnumField(it.simpleName.toString(), it.constantValue as Int))
    }
  }

  private fun name():String{
    val annotation = clazz.getAnnotation(LuaEnum::class.java)
    return if(annotation != null && annotation.alias.isNotBlank()) annotation.alias else shortName()
  }



  override fun headerCode(): String {
    return GenerateUtil.headerCode(this)
  }

  override fun sourceCode(): String {
    return """
      |#include "${fileName()}.h"
      |#include <jni.h>
      |#include "luajni.h"
      |#include "lua.h"
      |
      |${injectMethodCode()}
      |
      |${registerCode()}
      |
      |${unregisterCode()}
    """.trimMargin()
  }


  private fun injectMethodCode():String{
    return """
      |void ${injectToLuaMethodName()}(struct lua_State*L,JNIEnv*env,void*_){
      |  lua_createtable(L,0,${fields.size});
      |${fields.joinToString("\n") { "lua_pushinteger(L,${it.value});lua_setfield(L,-2,\"${it.name}\");" }.mIndent(2)}
      |  lua_setglobal(L,"${name()}");
      |}
    """.trimMargin()
  }

  override fun className(): String {
    return getJvmName(clazz)
  }

  override fun fileName(): String {
    return className().replace(".","_").replace("$","__")
  }

  private fun registerCode():String{
    return """
      |int register_${injectToLuaMethodName()}(JNIEnv*env){
      |  luaJniRegister("${className()}",${injectToLuaMethodName()},NULL);
      |  return 1;
      |}
    """.trimMargin()
  }

  private fun unregisterCode():String{
    return """
      |int unregister_${injectToLuaMethodName()}(JNIEnv*env){
      |  luaJniUnregister("${className()}");
      |  return 1;
      |}
    """.trimMargin()
  }

  override fun injectToLuaMethodName(): String {
    return "inject_${fileName()}"
  }
}