package top.lizhistudio.annotation.processor

import top.lizhistudio.annotation.processor.ClassElementMetaData.Companion.toCommonField
import top.lizhistudio.annotation.processor.GenerateUtil.addPutBackObject
import top.lizhistudio.annotation.processor.GenerateUtil.generateGetField
import top.lizhistudio.annotation.processor.GenerateUtil.generateReleaseContextCode
import top.lizhistudio.annotation.processor.GenerateUtil.getFieldIdCode
import top.lizhistudio.annotation.processor.GenerateUtil.getJvmName
import top.lizhistudio.annotation.processor.GenerateUtil.mIndent
import top.lizhistudio.annotation.processor.GenerateUtil.toCFieldName
import top.lizhistudio.annotation.processor.data.CommonField
import top.lizhistudio.annotation.processor.data.GeneratorContext
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

class EnvironmentGenerator(private val clazz:TypeElement): CommonGenerator() {
  private val fields = mutableListOf<CommonField>()
  init{
    clazz.enclosedElements.filter {it.kind == ElementKind.FIELD && it.simpleName.toString() != "INSTANCE"}.forEach {
//      println("field:${it.simpleName}  ${isStaticField(it as VariableElement)}  ,${it.modifiers.contains(Modifier.STATIC)} ")
      fields.add(toCommonField(it as VariableElement))
    }
  }

  override fun sourceCode(): String {
    return """
      |${includeCode()}
      |${classInfoCode()}
      |${injectToLuaCode()}
      |${registerCode()}
      |${unregisterCode()}
    """.trimMargin()
  }
  private fun includeCode():String{
    return """
    |#include"${fileName()}.h"
    |#include"lua.h"
    |#include"lauxlib.h"
    |#include"lualib.h"
    |#include"luajni.h"
    |#include <stdlib.h>
    """.trimMargin()
  }
  private fun classInfoCode():String{
    return """
      |typedef struct ClassInfo{
      |  const char* name;
      |  int64_t id;
      |${fields.joinToString("\n"){field->"  jfieldID ${toCFieldName(field)};" }}
      |}ClassInfo;
    """.trimMargin()
  }
  private fun injectToLuaCode():String {
    val context = GeneratorContext()
    context.addPutBackObject("clazz")
    val setFieldCode = fields.joinToString("\n"){
      """
        |{
        |${generateGetField(it,context).mIndent(2)}
        |  lua_setglobal(L,"${it.name}");
        |}
      """.trimMargin()
    }
    return """
      |static int ${injectToLuaMethodName()}(lua_State* L,JNIEnv* env,void*ptr ){
      |  ClassInfo* classInfo = (ClassInfo*)ptr;
      |  jclass clazz = luaJniTakeObject(env,classInfo->id);
      |${setFieldCode.mIndent(2)}
      |${generateReleaseContextCode(context).mIndent(2)}
      |  return 0;
      |}
      """.trimMargin()
  }
  private fun registerCode():String{
    return """
      |int register_${injectToLuaMethodName()}(JNIEnv*env){
      |  ClassInfo* classInfo = (ClassInfo*)malloc(sizeof(ClassInfo));
      |  jclass clazz = (*env)->FindClass(env,"${className().replace(".","/")}");
      |  classInfo->name = "${className()}";
      |  classInfo->id = luaJniCacheObject(env,(jobject)clazz);
      |${fields.joinToString("\n"){ getFieldIdCode(it)}.mIndent(2)}
      |  (*env)->DeleteLocalRef(env,clazz);
      |  luaJniRegister("${className()}",${injectToLuaMethodName()},classInfo);
      |  return 1;
      |}
    """.trimMargin()
  }
  private fun unregisterCode():String{
    return """
      |int unregister_${injectToLuaMethodName()}(JNIEnv*env){
      |  ClassInfo* classInfo = (ClassInfo*)luaJniUnregister("${className()}");
      |  if(classInfo){
      |    luaJniReleaseObject(env,classInfo->id);
      |    free(classInfo);
      |  }
      |  return 1;
      |}
    """.trimMargin()
  }

  override fun className(): String {
    return getJvmName(clazz)
  }
}