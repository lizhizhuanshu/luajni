package top.lizhistudio.annotation.processor

import top.lizhistudio.annotation.processor.GenerateUtil.addDeleteLocalRef
import top.lizhistudio.annotation.processor.GenerateUtil.addPutBackObject
import top.lizhistudio.annotation.processor.GenerateUtil.generateArrayElementTypeCode
import top.lizhistudio.annotation.processor.GenerateUtil.generateParametersName
import top.lizhistudio.annotation.processor.GenerateUtil.generateReleaseContextCode
import top.lizhistudio.annotation.processor.GenerateUtil.getFieldIdCode
import top.lizhistudio.annotation.processor.GenerateUtil.getMethodIdCode
import top.lizhistudio.annotation.processor.GenerateUtil.java2luaException
import top.lizhistudio.annotation.processor.GenerateUtil.jniMethodType
import top.lizhistudio.annotation.processor.GenerateUtil.mIndent
import top.lizhistudio.annotation.processor.GenerateUtil.shortName
import top.lizhistudio.annotation.processor.GenerateUtil.toCFieldName
import top.lizhistudio.annotation.processor.GenerateUtil.toCMethodName
import top.lizhistudio.annotation.processor.GenerateUtil.toCParameterName
import top.lizhistudio.annotation.processor.data.CommonField
import top.lizhistudio.annotation.processor.data.CommonMethod
import top.lizhistudio.annotation.processor.data.CommonParameter
import top.lizhistudio.annotation.processor.data.CommonType
import top.lizhistudio.annotation.processor.data.GeneratorContext
import kotlin.math.max

class ClassCodeGenerator(private val clazz:ClassMetaData):Generator,
  MetaData by clazz{
  override fun headerCode(): String {
    val defineH = "${fileName()}_h".uppercase()
    val code = """
    |#ifndef $defineH
    |#define $defineH
    |#ifdef __cplusplus
    |extern "C" {
    |#endif
    |#include<jni.h>
    |int register_${injectToLuaMethodName()}(JNIEnv*env);
    |int unregister_${injectToLuaMethodName()}(JNIEnv*env);
    |#ifdef __cplusplus
    |}
    |#endif
    |
    |#endif //$defineH
    """.trimMargin()
    return code
  }
  override fun sourceCode(): String {
    return """
    |${includeCode()}
    |
    |${cClassInfo()}
    |
    |${methodFunctionCode()}
    |
    |${indexMethodCode()}
    |
    |${newIndexMethodCode()}
    |
    |${constructorMethodCode()}
    |
    |${injectMethodCode()}
    |
    |${registerCode()}
    |
    |${unregisterCode()}
    """.trimMargin()
  }
  private fun includeCode():String{
    val code = """
    |#include "${fileName()}.h"
    |#include "lua.h"
    |#include "lauxlib.h"
    |#include "lualib.h"
    |#include "luajni.h"
    |#include <stdlib.h>
    |#include <string.h>
    """.trimMargin()
    return code
  }
  private fun cClassInfo():String{
    val fieldsCode = clazz.fields().joinToString("\n"){ field ->
      "jfieldID ${toCFieldName(field)};"
    }
    val methodsCode = clazz.methods().joinToString("\n"){ method ->
      "jmethodID ${toCMethodName(method)};"
    }
    val code = """
    |typedef struct {
    |  const char* name;
    |  int64_t id;
    |${fieldsCode.mIndent(2)}
    |${methodsCode.mIndent(2)}
    |}ClassInfo;
    """.trimMargin()
    return code
  }
  private fun methodFunctionCode():String{
    val methods = mutableListOf<CommonMethod>()
    clazz.methods().forEach {
      if(it.toField) return@forEach
      methods.add(it)
    }
    return methods.joinToString("\n"){ method ->
      methodFunctionCode(method)
    }
  }
  private fun methodFunctionCode(method:CommonMethod):String{
    val context = GeneratorContext()
    context.addPutBackObject("obj")
    return """
      |static int ${methodFunctionName(method.name)}(lua_State*L){
      |  JNIEnv* env = luaJniGetEnv(L);
      |  JavaObject* object = (JavaObject*)luaL_checkudata(L,1,"${className()}");
      |  jobject obj = luaJniTakeObject(env,object->id);
      |  ClassInfo* classInfo = (ClassInfo*)lua_touserdata(L,lua_upvalueindex(1));
      |${parametersCheckCode(method,context).mIndent(2)}
      |${parametersInitCode(method,context).mIndent(2)}
      |${GenerateUtil.generateCallMethodCode(method,context).mIndent(2)}
      |${generateReleaseContextCode(context).mIndent(2)}
      |  return 1;
      |}
      """.trimMargin()
  }
  private fun indexMethodCode():String{
    val context = GeneratorContext()
    context.addPutBackObject("obj")
    var count = 0
    val fieldsCode = clazz.fields().joinToString("\n"){ field ->
      if(count++ ==0) fieldIndexCode(field,context) else "else "+ fieldIndexCode(field,context)
    }
    val methodsCode = clazz.methods().joinToString("\n"){ method ->
      if(count++ ==0) methodIndexCode(method,context) else "else "+ methodIndexCode(method,context)
    }
    return """
    |static int _indexMethod(lua_State*L){
    |  ClassInfo * classInfo = (ClassInfo *) lua_touserdata(L,lua_upvalueindex(1));
    |  JavaObject* object = (JavaObject*)luaL_checkudata(L,1,"${className()}");
    |  size_t keySize = 0;
    |  const char* $KEY_NAME = luaL_checklstring(L,2,&keySize);
    |  jobject obj = luaJniTakeObject(env,object->id);
    |  JNIEnv* env = luaJniGetEnv(L);
    |${fieldsCode.mIndent(2)}
    |${methodsCode.mIndent(2)}
    |  else{
    |    lua_pushnil(L);
    |  }
    |${generateReleaseContextCode(context,0).mIndent(2)}
    |  return 1;
    |}
    """.trimMargin()
  }

  private fun newIndexMethodCode():String{
    val context = GeneratorContext()
    context.addPutBackObject("obj")
    var memberCount = 0
    val fields = clazz.fields().filter { !it.readonly}
    val methods = clazz.methods().filter { it.toField && it.parameters.size==1 }
    val fieldsCode = fields.joinToString("\n"){ field ->
      if(memberCount++  == 0) fieldNewIndexCode(field,context.clone()) else "else "+ fieldNewIndexCode(field,context.clone())
    }
    val methodsCode = methods.joinToString("\n"){ method ->
      if(memberCount++ ==0) methodNewIndexCode(method,context.clone()) else "else "+ methodNewIndexCode(method,context.clone())
    }
    val memberCode = if(fields.isEmpty() && methods.isEmpty()) "if(0){}" else
      """
      |$fieldsCode
      |$methodsCode
      """.trimMargin()

    return """
    |static int _newIndexMethod(lua_State*L){
    |  ClassInfo * classInfo = (ClassInfo *) lua_touserdata(L,lua_upvalueindex(1));
    |  JavaObject* object = (JavaObject*)luaL_checkudata(L,1,"${className()}");
    |  size_t keySize = 0;
    |  const char* $KEY_NAME = luaL_checklstring(L,2,&keySize);
    |  jobject obj = luaJniTakeObject(env,object->id);
    |  JNIEnv* env = luaJniGetEnv(L);
    |${memberCode.mIndent(2)}
    |  else{
    |${generateReleaseContextCode(context,0).mIndent(4)}
    |    luaL_error(L,"Can't find member %s",$KEY_NAME);
    |  }
    |${generateReleaseContextCode(context,0).mIndent(2)}
    |  return 0;
    |}
    """.trimMargin()
  }

  private fun fieldNewIndexCode(field:CommonField,context:GeneratorContext):String{
    val top = context.needRelease.size
    return memberNameCompareCode(field.name,"""
      |${parameterCheckTypeCode(field.name,field.type,context,3)}
      |${parameterInitCode(field.name,field.type,context,3)}
      |${setFieldCode(field)}
      |${java2luaException(context)}
      |${generateReleaseContextCode(context, max(1,top))}
    """.trimMargin())
  }

  private fun methodNewIndexCode(method: CommonMethod, context:GeneratorContext):String{
    val top = context.needRelease.size
    return memberNameCompareCode(method.name,"""
      |${parameterCheckTypeCode(method.parameters[0],context,3)}
      |${parametersInitCode(method,context,3)}
      |${GenerateUtil.generateCallMethodCode(method,context)}
      |${generateReleaseContextCode(context,max(1,top))}
    """.trimMargin())
  }

  private fun initClassInfoCode():String{
    val initFieldsCode = clazz.fields().joinToString("\n"){
      getFieldIdCode(it)
    }
    val initMethodIdCode = clazz.methods().joinToString("\n"){
      getMethodIdCode(it)
    }
    return """
      |$initFieldsCode
      |$initMethodIdCode
    """.trimMargin()
  }

  private fun constructorFunctionName() = "constructor_call"
  private fun constructorMethodCode():String{
    if(clazz.constructors().isEmpty()) return ""
    val context = GeneratorContext()
    context.addPutBackObject("clazz")
    return """
      |static int ${constructorFunctionName()}(lua_State*L){
      |  JNIEnv* env = luaJniGetEnv(L);
      |  jclass clazz = (*env)->FindClass(env,"${className().replace(".","/")}");
      |${java2luaException(context).mIndent(2)}
      |  jobject obj = NULL;
      |${eachConstructorMethod(context).mIndent(2)}
      |  if(obj != NULL){
      |    JavaObject* jniObject = (JavaObject*)lua_newuserdata(L,sizeof(JavaObject));
      |    jniObject->id = luaJniCacheObject(env,obj);
      |    (*env)->DeleteLocalRef(env,obj);
      |    luaL_setmetatable(L,"${className()}");
      |  }else{
      |    lua_pushnil(L);
      |  }
      |${generateReleaseContextCode(context).mIndent(2)}
      |  return 1;
      |}
    """.trimMargin()
  }
  private fun eachConstructorMethod(context: GeneratorContext):String{
    var count = 0
    return clazz.constructors().joinToString("\n"){ constructor ->
      if(count++ >0) "else "+constructorMethodCode(constructor,context.clone()) else constructorMethodCode(constructor,context.clone())
    } + """
      |else{
      |${generateReleaseContextCode(context).mIndent(2)}
      |  luaL_error(L,"Can't find constructor");
      |}
    """.trimMargin()
  }
  private fun constructorMethodEqualsCode(constructor: CommonMethod,context: GeneratorContext):String{
    return if(constructor.parameters.isEmpty()) "lua_gettop(L) == 0" else {
      val parameters = constructor.parameters.withIndex().joinToString("&&"){(index,parameter)->
        isParameterTypeCode(parameter.type,index+1,context)
      }
      "lua_gettop(L) == ${constructor.parameters.size+1} &&\n$parameters"
    }
  }
  private fun isParameterTypeCode(type:CommonType,index:Int,context: GeneratorContext):String{
    if(type.dimensions >0){
      return "(lua_isnil(L,$index) || equalsJavaArray(lua_testudata(L,$index,\"JavaArray\"),${type.dimensions},\"${type.name}\",${generateArrayElementTypeCode(type.name)}))"
    }
    return when(type.name){
      "boolean" ->  "lua_isboolean(L,$index)"
      "byte" ->  "lua_isinteger(L,$index)"
      "short" -> "lua_isinteger(L,$index)"
      "int" ->  "lua_isinteger(L,$index)"
      "long" ->  "lua_isinteger(L,$index)"
      "float" ->  "lua_isnumber(L,$index)"
      "double" ->  "lua_isnumber(L,$index)"
      "java.lang.String" ->  "(lua_isnil(L,$index) || lua_isstring(L,$index))"
      else -> "(lua_isnil(L,$index) || lua_testudata(L,$index,\"${type.name}\") != NULL)"
    }
  }
  private fun constructorMethodCode(constructor:CommonMethod,context: GeneratorContext):String{
    return """
      |if(${constructorMethodEqualsCode(constructor,context)}){
      |${parametersInitCode(constructor,context,1,true).mIndent(2)}
      |  jmethodID methodID = (*env)->GetMethodID(env,clazz,"<init>","${jniMethodType(constructor)}");
      |${java2luaException(context).mIndent(2)}
      |  obj = (*env)->NewObject(env,clazz,methodID${generateParametersName(constructor.parameters)});
      |${java2luaException(context).mIndent(2)}
      |}
    """.trimMargin()
  }


  private fun injectMethodCode():String{
    val context = GeneratorContext()
    return """
    |static int ${injectToLuaMethodName()}(struct lua_State*L,JNIEnv*env,void*classInfo){
    |  if(luaL_newmetatable(L,"${className()}")){
    |    luaL_Reg meta[] = {
    |      {"__index",_indexMethod},
    |      {"__newindex",_newIndexMethod},
    |      {"__gc",luaJniJavaObjectGc},
    |      {NULL,NULL}
    |    };
    |    lua_pushlightuserdata(L,classInfo);
    |    luaL_setfuncs(L,meta,1);
    |  }
    |  lua_pop(L,1);
    |${constructorCode(context).mIndent(2)}
    |  return 0;
    |}
    """.trimMargin()
  }
  private fun registerCode():String{
    return """
      |int register_${injectToLuaMethodName()}(JNIEnv*env){
      |  ClassInfo* classInfo = (ClassInfo*)malloc(sizeof(ClassInfo));
      |  classInfo->name = "${className()}";
      |  jclass clazz = (*env)->FindClass(env,"${className().replace(".","/")}");
      |${initClassInfoCode().mIndent(2)}
      |  classInfo->id = luaJniCacheObject(env,clazz);
      |  (*env)->DeleteLocalRef(env,clazz);
      |  luaJniRegister("${className()}",${injectToLuaMethodName()},classInfo);
      |  return 1;
      |}
    """.trimMargin()
  }

  private fun unregisterCode():String{
    return """
      |int unregister_${injectToLuaMethodName()}(JNIEnv*env){
      |  ClassInfo* classInfo =luaJniUnregister("${className()}");
      |  if(classInfo != NULL){
      |    luaJniReleaseObject(env,classInfo->id);
      |    free(classInfo);
      |  }
      |  return 1;
      |}
    """.trimMargin()
  }

  private fun constructorCode(context: GeneratorContext):String{
    context.addDeleteLocalRef("clazz")
    return if(clazz.autoRegister()) """
      |jclass clazz = (*env)->FindClass(env,"${clazz.className().replace(".","/")}");
      |jmethodID constructor = (*env)->GetMethodID(env,clazz,"<init>","()V");
      |${java2luaException(context).mIndent(2)}
      |jobject obj = (*env)->NewObject(env,clazz,constructor);
      |${java2luaException(context).mIndent(2)}
      |if(obj != NULL){
      |  JavaObject* jniObject = (JavaObject*)lua_newuserdata(L,sizeof(JavaObject));
      |  jniObject->id = luaJniCacheObject(env,obj);
      |  (*env)->DeleteLocalRef(env,obj);
      |  luaL_setmetatable(L,"${className()}");
      |  lua_setglobal(L,"${clazz.shortName()}");
      |}
      |${generateReleaseContextCode(context)}
    """.trimMargin()
    else if(clazz.constructors().isNotEmpty())
      """
      |lua_pushcfunction(L,${constructorFunctionName()});
      |lua_setglobal(L,${clazz.shortName()});
      """.trimMargin()
    else ""
  }



  companion object {
    private const val KEY_NAME = "keyStr"

    private fun memberNameCompareCode(memberName:String, memberCode:String): String {
      return """
      |if(strcmp($KEY_NAME,"$memberName") == 0){
      |${memberCode.mIndent(2)}
      |}
      """.trimMargin()
    }

    private fun fieldIndexCode(field: CommonField, context: GeneratorContext): String {
      return memberNameCompareCode(field.name,GenerateUtil.generateGetField(field,context))
    }


    private fun simpleMethodIndexCode(method: CommonMethod): String {
      val code = """
      |lua_pushvalue(L,lua_upvalueindex(1));
      |lua_pushcclosure(L,${methodFunctionName(method.name)},1);
      """.trimMargin()
      return memberNameCompareCode(method.name,code)
    }

    private fun methodToFieldIndexCode(method: CommonMethod, context: GeneratorContext): String {
      if(method.parameters.isNotEmpty()) return ""
      return memberNameCompareCode(method.name,GenerateUtil.generateCallMethodCode(method,context))
    }

    private fun methodIndexCode(method: CommonMethod, context: GeneratorContext): String {
      if(method.toField) return methodToFieldIndexCode(method,context)
      return simpleMethodIndexCode(method)
    }

    private fun parameterCheckTypeCode(parameterName:String,type:CommonType,context: GeneratorContext,
                                       index: Int):String{
      val generateSimpleTypeCheck = {name:String ->
        """
          |if(!lua_is${name}(L,$index)){
          |${generateReleaseContextCode(context).mIndent(2)}
          |  luaL_error(L,"Parameter $index must be a $name");
          |};
        """.trimMargin()
      }

      if(type.dimensions > 0){
        val jniObjectName = jniObjectParameterName(parameterName)
        return """
          |JavaArray* $jniObjectName = NULL;
          |if(!lua_isnoneornil(L,$index)){
          |  $jniObjectName = (JavaArray*)luaL_testudata(L,$index,"JavaArray");
          |  if($jniObjectName  == NULL){
          |${generateReleaseContextCode(context).mIndent(4)}
          |    luaL_error(L,"Parameter $index must be a JavaArray");
          |  }
          |}
        """.trimMargin()
      }

      return when(type.name){
        "boolean" -> generateSimpleTypeCheck("boolean")
        "byte" -> generateSimpleTypeCheck("integer")
        "short" -> generateSimpleTypeCheck("integer")
        "int" -> generateSimpleTypeCheck("integer")
        "long" -> generateSimpleTypeCheck("integer")
        "float" -> generateSimpleTypeCheck("number")
        "double" -> generateSimpleTypeCheck("number")
        "java.lang.String" -> """
          |if(!lua_isnoneornil(L,$index)&&!lua_isstring(L,$index)){
          |${generateReleaseContextCode(context).mIndent(2)}
          |  luaL_error(L,"Parameter $index must be a string");
          |}
        """.trimMargin()
        else ->{
          val jniObjectName = jniObjectParameterName(parameterName)
          """
          |JavaObject* $jniObjectName = NULL;
          |if(!lua_isnoneornil(L,$index)){
          |  $jniObjectName = (JavaObject*)luaL_testudata(L,$index,"${type.name}");
          |  if($jniObjectName  == NULL){
          |${generateReleaseContextCode(context).mIndent(4)}
          |    luaL_error(L,"Parameter $index must be a ${type.name}");
          |  }
          |}
        """.trimMargin()
        }

      }
    }
    private fun parameterCheckTypeCode(parameter:CommonParameter,
                                       context: GeneratorContext, index:Int):String{
      return parameterCheckTypeCode(parameter.name,parameter.type,context,index)
    }
    private fun parameterInitCode(parameterName: String,type:CommonType,
                                  context: GeneratorContext,index:Int,checkJniObjectParameter:Boolean = false):String{
      val simpleParamInit = { name:String->
        val jniType = GenerateUtil.toJniTypeName(type.name)
        val paramName = toCParameterName(parameterName)
        "$jniType $paramName = lua_to${name}(L,$index);"
      }
      return when(type.name){
        "boolean" -> simpleParamInit("boolean")
        "byte" -> simpleParamInit("integer")
        "short" -> simpleParamInit("integer")
        "int" -> simpleParamInit("integer")
        "long" -> simpleParamInit("integer")
        "float" -> simpleParamInit("number")
        "double" -> simpleParamInit("number")
        "java.lang.String" -> {
          val paramName = toCParameterName(parameterName)
          context.addDeleteLocalRef(paramName)
          """
            |jstring $paramName = NULL;
            |if(lua_isstring(L,$index)){
            |  const char* luaParam_$paramName = lua_tostring(L,$index);
            |  $paramName = (*env)->NewStringUTF(env,luaParam_$paramName);
            |  if(luaJniCatchJavaException(L,env)){
            |${generateReleaseContextCode(context).mIndent(4)}
            |    lua_error(L);
            |  }
            |}
          """.trimMargin()
        }
        else -> {
          val paramName = toCParameterName(parameterName)
          val jniType = GenerateUtil.toJniTypeName(type.name)
          val jniParameter = jniObjectParameterName(parameterName)
          val checkJNIObjectCode = if(checkJniObjectParameter)"""
            |$jniParameter = NULL;
            |if(!lua_isnoneornil(L,$index)){
            |  $jniParameter = (JavaObject*)luaL_testudata(L,$index,"${type.name}");
            |  if($jniParameter == NULL){
            |${generateReleaseContextCode(context).mIndent(4)}
            |    luaL_error(L,"Parameter $index must be a ${type.name}");
            |  }
            |}
          """.trimMargin() else ""
          context.addPutBackObject(paramName)
          """
            |$jniType $paramName = NULL;
            |$checkJNIObjectCode
            |if($jniParameter != NULL){
            |  $paramName = luaJniTakeObject($jniParameter->id);
            |}
          """.trimMargin()
        }
      }
    }

    private fun parametersInitCode(method: CommonMethod, context: GeneratorContext,indexShift:Int=2,checkJniObjectParameter: Boolean=false):String{
      return method.parameters.withIndex().joinToString("\n"){ (index,parameter) ->
        parameterInitCode(parameter.name,parameter.type,context,index+indexShift,checkJniObjectParameter)
      }
    }
    private fun parametersCheckCode(method:CommonMethod,context: GeneratorContext):String{
      return if(method.parameters.isEmpty()) "" else
        method.parameters.withIndex().joinToString("\n"){ (index,parameter) ->
          parameterCheckTypeCode(parameter,context,index+2)
        }
    }

    private fun methodFunctionName(methodName:String):String{
      return "method_${methodName}"
    }
    private fun jniObjectParameterName(parameterName:String):String {
      return "jniObj_${parameterName}"
    }

    private fun setFieldCode(field:CommonField):String{
      val fieldName = toCFieldName(field)
      val paramName = toCParameterName(field.name)
      return when(field.type.name){
        "boolean" -> "(*env)->SetBooleanField(env,obj,classInfo->$fieldName,$paramName);"
        "byte" -> "(*env)->SetByteField(env,obj,classInfo->$fieldName,$paramName);"
        "short" -> "(*env)->SetShortField(env,obj,classInfo->$fieldName,$paramName);"
        "int" -> "(*env)->SetIntField(env,obj,classInfo->$fieldName,$paramName);"
        "long" -> "(*env)->SetLongField(env,obj,classInfo->$fieldName,$paramName);"
        "float" -> "(*env)->SetFloatField(env,obj,classInfo->$fieldName,$paramName);"
        "double" -> "(*env)->SetDoubleField(env,obj,classInfo->$fieldName,$paramName);"
        else -> "(*env)->SetObjectField(env,obj,classInfo->$fieldName,$paramName);"
      }
    }
  }
}