package top.lizhistudio.annotation.processor

import top.lizhistudio.annotation.processor.GenerateUtil.addPutBackObject
import top.lizhistudio.annotation.processor.GenerateUtil.generateReleaseContextCode
import top.lizhistudio.annotation.processor.GenerateUtil.getJvmName
import top.lizhistudio.annotation.processor.GenerateUtil.getMethodIdCode
import top.lizhistudio.annotation.processor.GenerateUtil.isKotlinObject
import top.lizhistudio.annotation.processor.GenerateUtil.mIndent
import top.lizhistudio.annotation.processor.GenerateUtil.methodCallName
import top.lizhistudio.annotation.processor.GenerateUtil.setGlobalFunctionCode
import top.lizhistudio.annotation.processor.GenerateUtil.toCMethodName
import top.lizhistudio.annotation.processor.GenerateUtil.toJniTypeName
import top.lizhistudio.annotation.processor.data.CommonMethod
import top.lizhistudio.annotation.processor.data.GeneratorContext
import javax.lang.model.element.TypeElement

class FunctionsCodeGenerator(private val clazz:TypeElement):Generator,FunctionContainer {
  private val functions = mutableListOf<CommonMethod>()

  override fun headerCode(): String {
    return GenerateUtil.headerCode(this)
  }

  override fun sourceCode(): String {
    return """
      |#include "${fileName()}.h"
      |#include <jni.h>
      |#include "luajni.h"
      |#include "lua.h"
      |#include <stdlib.h>
      |#include "lualib.h"
      |#include "lauxlib.h"
      |
      |${classInfoCode()}
      |
      |${methodCallCode()}
      |
      |${injectMethodCode()}
      |
      |${registerCode()}
      |
      |${unregisterCode()}
    """.trimMargin()
  }
  private fun classInfoCode():String{
    return """
      |typedef struct ClassInfo{
      |  const char* name;
      |  int64_t id;
      |  ${if(isKotlinObject(clazz)) "int64_t instanceId;" else ""}
      |${functions.joinToString("\n"){"jmethodID ${toCMethodName(it)};"}.mIndent(2)}
      |}ClassInfo;
    """.trimMargin()
  }

  private fun injectMethodCode():String{
    return """
      |void ${injectToLuaMethodName()}(struct lua_State*L,JNIEnv*env,void*classInfo){
      |${functions.joinToString("\n") { setGlobalFunctionCode(it) }.mIndent(2)}
      |}
    """.trimMargin()
  }

  private fun methodCallCode():String{
    val isKotlinObject = isKotlinObject(clazz)
    return functions.joinToString("\n") { methodCallCode(it,isKotlinObject) }
  }

  override fun className(): String {
    return getJvmName(clazz)
  }

  override fun fileName(): String {
    return className().replace(".","_").replace("$","__")
  }

  override fun injectToLuaMethodName(): String {
    return "inject_${fileName()}"
  }

  private fun registerCode():String{
    val initMethodIdCode = functions.joinToString("\n"){
      getMethodIdCode(it)
    }
    val initInstanceId = if(isKotlinObject(clazz))
      """
        |jfieldID instanceFieldID = (*env)->GetStaticFieldID(env,clazz,"INSTANCE","L${className().replace(".","/")};");
        |jobject instance = (*env)->GetStaticObjectField(env,clazz,instanceFieldID);
        |classInfo->instanceId = luaJniCacheObject(env,instance);
        |(*env)->DeleteLocalRef(env,instance);
      """.trimMargin()
    else ""

    return """
      |int register_${injectToLuaMethodName()}(JNIEnv*env){
      |  ClassInfo* classInfo = (ClassInfo*)malloc(sizeof(ClassInfo));
      |  classInfo->name = "${className()}";
      |  jclass clazz = (*env)->FindClass(env,"${className().replace(".","/")}");
      |  classInfo->id = luaJniCacheObject(env,clazz);
      |${initInstanceId.mIndent(2)}
      |${initMethodIdCode.mIndent(2)}
      |  (*env)->DeleteLocalRef(env,clazz);
      |  luaJniRegister("${className()}",${injectToLuaMethodName()},classInfo);
      |  return 1;
      |}
    """.trimMargin()
  }

  private fun unregisterCode():String{
    val releaseInstance = if(isKotlinObject(clazz))
      """
        |luaJniReleaseObject(env,classInfo->instanceId);
      """.trimMargin()
    else ""
    return """
      |int unregister_${injectToLuaMethodName()}(JNIEnv*env){
      |  ClassInfo* classInfo = (ClassInfo*)luaJniUnregister("${className()}");
      |  if(classInfo){
      |    $releaseInstance
      |    luaJniReleaseObject(env,classInfo->id);
      |    free(classInfo);
      |  }
      |  return 1;
      |}
    """.trimMargin()
  }

  companion object{

    private fun methodCallCode(method:CommonMethod,isKotlinObject:Boolean=false):String{
      val context = GeneratorContext()
      val objCode = if(isKotlinObject){
        context.addPutBackObject("obj")
        "jobject obj = luaJniTakeObject(env,classInfo->instanceId);"
      }else{
        context.addPutBackObject("clazz")
        "jclass clazz = luaJniTakeObject(env,classInfo->id);"
      }
      return """
        |static int ${methodCallName(method.name)}(lua_State*L){
        |  ClassInfo* classInfo = lua_touserdata(L,lua_upvalueindex(1));
        |  JNIEnv* env = luaJniGetEnv(L);
        |  $objCode
        |${GenerateUtil.parametersCheckCode(method,context,1).mIndent(2)}
        |${GenerateUtil.parametersInitCode(method,context,1,true).mIndent(2)}
        |${GenerateUtil.callMethodCode(method,context).mIndent(2)}
        |${generateReleaseContextCode(context).mIndent(2)}
        |  return 1;
        |}
      """.trimMargin()
    }
  }

  override fun putFunction(f: CommonMethod) {
    functions.add(f)
  }
}