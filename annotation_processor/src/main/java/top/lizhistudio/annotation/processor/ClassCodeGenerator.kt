package top.lizhistudio.annotation.processor

import top.lizhistudio.annotation.processor.GenerateUtil.addDeleteLocalRef
import top.lizhistudio.annotation.processor.GenerateUtil.addPutBackObject
import top.lizhistudio.annotation.processor.GenerateUtil.generateArrayElementTypeCode
import top.lizhistudio.annotation.processor.GenerateUtil.generateParametersName
import top.lizhistudio.annotation.processor.GenerateUtil.generateReleaseContextCode
import top.lizhistudio.annotation.processor.GenerateUtil.getConstructorCode
import top.lizhistudio.annotation.processor.GenerateUtil.getFieldIdCode
import top.lizhistudio.annotation.processor.GenerateUtil.getMethodIdCode
import top.lizhistudio.annotation.processor.GenerateUtil.java2luaException
import top.lizhistudio.annotation.processor.GenerateUtil.jniMethodType
import top.lizhistudio.annotation.processor.GenerateUtil.mIndent
import top.lizhistudio.annotation.processor.GenerateUtil.setGlobalFunctionCode
import top.lizhistudio.annotation.processor.GenerateUtil.shortName
import top.lizhistudio.annotation.processor.GenerateUtil.toCConstructorName
import top.lizhistudio.annotation.processor.GenerateUtil.toCFieldName
import top.lizhistudio.annotation.processor.GenerateUtil.toCMethodName
import top.lizhistudio.annotation.processor.GenerateUtil.toCParameterName
import top.lizhistudio.annotation.processor.data.CommonField
import top.lizhistudio.annotation.processor.data.CommonMethod
import top.lizhistudio.annotation.processor.data.CommonType
import top.lizhistudio.annotation.processor.data.GeneratorContext
import top.lizhistudio.annotation.processor.data.indexName
import kotlin.math.max

class ClassCodeGenerator(private val clazz:ClassMetaData):Generator,
  MetaData by clazz
  ,FunctionContainer
{
  private val functions = mutableListOf<CommonMethod>()
  private val sortedMethods = mutableListOf<MutableList<CommonMethod>>()
  private fun methodSort(method:CommonMethod){
    if(method.toField) return
    val indexName = method.indexName()
    var table = sortedMethods.find { it[0].indexName() == indexName }
    if(table == null){
      val newTable = mutableListOf<CommonMethod>()
      sortedMethods.add(newTable)
      table = newTable
    }
    if(table.isNotEmpty()) method.order = table.size
    table.add(method)
  }
  init{
    clazz.methods().forEach {
      methodSort(it)
    }
    clazz.constructors().withIndex().forEach { (index,constructor) ->
      if(index>0) constructor.order = index
    }
  }
  override fun headerCode(): String {
    return GenerateUtil.headerCode(this)
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
    val functionCode = functions.joinToString("\n"){ method ->
      "jmethodID ${toCMethodName(method)};"
    }
    val code = """
    |typedef struct ClassInfo{
    |  const char* name;
    |  int64_t id;
    |${fieldsCode.mIndent(2)}
    |${methodsCode.mIndent(2)}
    |${functionCode.mIndent(2)}
    |${clazz.constructors().joinToString("\n"){"jmethodID ${toCConstructorName(it)};"}.mIndent(2)}
    |}ClassInfo;
    """.trimMargin()
    return code
  }
  private fun methodFunctionCode():String{
    functions.forEach {
      methodSort(it)
    }
    return sortedMethods.joinToString("\n"){ method ->
      methodFunctionCode(method)
    }
  }
  private fun oneMethodCode(method:CommonMethod):String{
    val context = GeneratorContext()
    val initObject = if(!method.isStatic){
      context.addPutBackObject("obj")
      """
        |JavaObject* object = (JavaObject*)luaL_checkudata(L,1,"${className()}");
        |jobject obj = luaJniTakeObject(env,object->id);
      """.trimMargin()
    }else {
      context.addPutBackObject("clazz")
      """
      |jclass clazz = (jclass)luaJniTakeObject(env,classInfo->id);
      """.trimMargin()
    }
    val indexOrigin = if(method.isStatic) 1 else 2
    return """
      |if(${isParametersTypeCode(method,context,indexOrigin)}){
      |${initObject.mIndent(2)}
      |${GenerateUtil.parametersInitCode(method,context,indexOrigin).mIndent(2)}
      |${GenerateUtil.callMethodCode(method,context).mIndent(2)}
      |${generateReleaseContextCode(context).mIndent(2)}
      |  return 1;
      |}
    """.trimMargin()
  }
  private fun methodFunctionCode(methods:List<CommonMethod>):String{
    var count = 0
    val code = methods.joinToString("\n"){ method ->
      val code = oneMethodCode(method)
      if(count++ == 0) code else "else $code"
    }
    return """
      |static int ${methodFunctionName(methods[0].indexName())}(lua_State*L){
      |  JNIEnv* env = luaJniGetEnv(L);
      |  ClassInfo* classInfo = (ClassInfo*)lua_touserdata(L,lua_upvalueindex(1));
      |${code.mIndent(2)}
      |  else{
      |    luaL_error(L,"method ${methods[0].indexName()} parameter mismatch");
      |  }
      |  return 0;
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
    val methods = mutableListOf<CommonMethod>()
    methods.addAll(sortedMethods.map { it[0] })
    clazz.methods().forEach {
      if(it.toField && it.parameters.isEmpty()) methods.add(it)
    }
    val methodsCode = methods.joinToString("\n"){ method ->
      val code = methodIndexCode(method,context)
      if(count++ ==0) code else "else $code"
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
    return memberNameCompareCode(field.indexName(),"""
      |${GenerateUtil.parameterCheckTypeCode(field.name,field.type,context,3)}
      |${GenerateUtil.parameterInitCode(field.name,field.type,context,3)}
      |${setFieldCode(field)}
      |${java2luaException(context)}
      |${generateReleaseContextCode(context, max(1,top))}
    """.trimMargin())
  }

  private fun methodNewIndexCode(method: CommonMethod, context:GeneratorContext):String{
    val top = context.needRelease.size
    return memberNameCompareCode(method.indexName(),"""
      |${GenerateUtil.parameterCheckTypeCode(method.parameters[0],context,3)}
      |${GenerateUtil.parametersInitCode(method,context,3)}
      |${GenerateUtil.callMethodCode(method,context)}
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
    val initConstructorIdCode = clazz.constructors().joinToString("\n") {
      getConstructorCode(it)
    }
    return """
      |$initFieldsCode
      |$initMethodIdCode
      |$initConstructorIdCode
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
      |  ClassInfo* classInfo = (ClassInfo*)lua_touserdata(L,lua_upvalueindex(1));
      |  jclass clazz = (jclass)luaJniTakeObject(env,classInfo->id);
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
      val code = constructorMethodCode(constructor,context.clone())
      if(count++ >0) "else $code" else code
    } + """
      |else{
      |${generateReleaseContextCode(context).mIndent(2)}
      |  luaL_error(L,"Can't find constructor");
      |}
    """.trimMargin()
  }

  private fun constructorMethodCode(constructor:CommonMethod,context: GeneratorContext):String{
    return """
      |if(${constructorMethodEqualsCode(constructor,context)}){
      |${GenerateUtil.parametersInitCode(constructor,context,1,true).mIndent(2)}
      |  obj = (*env)->NewObject(env,clazz,classInfo->${toCConstructorName(constructor)}${generateParametersName(constructor.parameters)});
      |${java2luaException(context).mIndent(2)}
      |}
    """.trimMargin()
  }


  private fun injectMethodCode():String{
    val context = GeneratorContext()
    val injectFunctions = functions.joinToString("\n"){ method ->
      setGlobalFunctionCode(method)
    }
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
    |${injectFunctions.mIndent(2)}
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
      |lua_pushlightuserdata(L,classInfo);
      |lua_pushcclosure(L,${constructorFunctionName()},1);
      |lua_setglobal(L,"${clazz.shortName()}");
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
      return memberNameCompareCode(field.indexName(),GenerateUtil.generateGetField(field,context))
    }


    private fun simpleMethodIndexCode(method: CommonMethod): String {
      val code = """
      |lua_pushvalue(L,lua_upvalueindex(1));
      |lua_pushcclosure(L,${methodFunctionName(method.indexName())},1);
      """.trimMargin()
      return memberNameCompareCode(method.indexName(),code)
    }

    private fun methodToFieldIndexCode(method: CommonMethod, context: GeneratorContext): String {
      return memberNameCompareCode(method.indexName(),GenerateUtil.callMethodCode(method,context))
    }

    private fun methodIndexCode(method: CommonMethod, context: GeneratorContext): String {
      if(method.toField) return methodToFieldIndexCode(method,context)
      return simpleMethodIndexCode(method)
    }

    private fun methodFunctionName(methodName:String):String{
      return "method_${methodName}"
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

  override fun putFunction(f: CommonMethod) {
    functions.add(f)
  }

  private fun isParametersTypeCode(method:CommonMethod,
                                   context: GeneratorContext,
                                   origin:Int=1):String{
    return if(method.parameters.isEmpty()) "lua_gettop(L) == ${origin-1}" else {
      val parameters = method.parameters.withIndex().joinToString("&&"){ (index,parameter)->
        isParameterTypeCode(parameter.type,index+origin,context)
      }
      "lua_gettop(L) == ${method.parameters.size+origin-1} && $parameters"
    }
  }

  private fun constructorMethodEqualsCode(method: CommonMethod, context: GeneratorContext):String{
    return isParametersTypeCode(method,context,1)
  }
  private fun isParameterTypeCode(type:CommonType,index:Int,context: GeneratorContext):String{
    if(type.dimensions >0){
      return "(lua_isnil(L,$index) || equalsJavaArray(lua_testudata(L,$index,\"JavaArray\"),${type.dimensions},\"${type.name}\",${generateArrayElementTypeCode(type.name)}))"
    }
    val wrapperCode = {name:String->
      "(lua_isnil(L,$index) || lua_is${name}(L,$index))"
    }
    return when(type.name){
      "boolean" ->  "lua_isboolean(L,$index)"
      "byte" ->  "lua_isinteger(L,$index)"
      "short" -> "lua_isinteger(L,$index)"
      "int" ->  "lua_isinteger(L,$index)"
      "long" ->  "lua_isinteger(L,$index)"
      "float" ->  "lua_isnumber(L,$index)"
      "double" ->  "lua_isnumber(L,$index)"
      "java.lang.String" ->  "(lua_isnil(L,$index) || lua_type(L,$index) == LUA_TSTRING)"
      "java.lang.Boolean"-> wrapperCode("boolean")
      "java.lang.Byte"-> wrapperCode("integer")
      "java.lang.Short"-> wrapperCode("integer")
      "java.lang.Integer"-> wrapperCode("integer")
      "java.lang.Long"-> wrapperCode("integer")
      "java.lang.Float"-> wrapperCode("number")
      "java.lang.Double"-> wrapperCode("number")
      "java.lang.Character" -> wrapperCode("integer")
      else -> "(lua_isnil(L,$index) || lua_testudata(L,$index,\"${type.name}\") != NULL)"
    }
  }
}