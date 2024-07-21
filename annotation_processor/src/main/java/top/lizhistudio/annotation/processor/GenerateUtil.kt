package top.lizhistudio.annotation.processor


import androidx.privacysandbox.tools.kotlinx.metadata.jvm.KotlinClassMetadata
import top.lizhistudio.annotation.processor.data.CommonField
import top.lizhistudio.annotation.processor.data.CommonMethod
import top.lizhistudio.annotation.processor.data.CommonParameter
import top.lizhistudio.annotation.processor.data.CommonType
import top.lizhistudio.annotation.processor.data.GeneratorContext
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement

object GenerateUtil {

  private fun generateCommonGetField(cFieldName:String, fieldType:String):String{
    return when(fieldType){
      "short" -> "luaJniPushShortField(L,env,obj,classInfo->$cFieldName)"
      "byte" -> "luaJniPushByteField(L,env,obj,classInfo->$cFieldName)"
      "int" -> "luaJniPushIntField(L,env,obj,classInfo->$cFieldName)"
      "long" -> "luaJniPushLongField(L,env,obj,classInfo->$cFieldName)"
      "float" -> "luaJniPushFloatField(L,env,obj,classInfo->$cFieldName)"
      "double" -> "luaJniPushDoubleField(L,env,obj,classInfo->$cFieldName)"
      "boolean" -> "luaJniPushBooleanField(L,env,obj,classInfo->$cFieldName)"
      "java.lang.String" -> "luaJniPushStringField(L,env,obj,classInfo->$cFieldName)"
      else -> "luaJniPushObjectField(L,env,obj,classInfo->$cFieldName,\"$fieldType\")"
    }
  }
  fun generateArrayElementTypeCode(elementType:String):String{
    return when(elementType){
      "boolean" -> "ARRAY_ELEMENT_TYPE::ELEMENT_BOOLEAN"
      "byte" -> "ARRAY_ELEMENT_TYPE::ELEMENT_BYTE"
      "short" -> "ARRAY_ELEMENT_TYPE::ELEMENT_SHORT"
      "int" -> "ARRAY_ELEMENT_TYPE::ELEMENT_INT"
      "long" -> "ARRAY_ELEMENT_TYPE::ELEMENT_LONG"
      "float" -> "ARRAY_ELEMENT_TYPE::ELEMENT_FLOAT"
      "double" -> "ARRAY_ELEMENT_TYPE::ELEMENT_DOUBLE"
      "string" -> "ARRAY_ELEMENT_TYPE::ELEMENT_STRING"
      else -> "ARRAY_ELEMENT_TYPE::ELEMENT_OBJECT"
    }
  }
  private fun generateGetField(cFieldName:String, fieldType:String, dimensions:Int):String{
    if(dimensions == 0){
      return generateCommonGetField(cFieldName, fieldType)
    }
    val elementType = generateArrayElementTypeCode(fieldType)
    return "luaJniPushArrayField(L,env,obj,classInfo->$cFieldName,\"$fieldType\",$dimensions,$elementType)"
  }
  fun generateGetField(field:CommonField,context: GeneratorContext):String{
    return """
      |if(${generateGetField(toCFieldName(field),field.type.name,field.type.dimensions)} == 0){
      |${generateReleaseContextCode(context).mIndent(2)}
      |  lua_error(L);
      |}
    """.trimMargin()
  }

  fun toCFieldName(field:CommonField):String{
    return "m_${field.name}"
  }
  fun toCMethodName(method:CommonMethod):String{
    return "m_${method.name}"
  }
  fun toCParameterName(parameterName:String):String{
    return "p_${parameterName}"
  }

  fun toJniTypeName(type:String):String{
    return when(type){
      "void" -> "void"
      "boolean" -> "jboolean"
      "byte" -> "jbyte"
      "char" -> "jchar"
      "short" -> "jshort"
      "int" -> "jint"
      "long" -> "jlong"
      "float" -> "jfloat"
      "double" -> "jdouble"
      else -> "jobject"
    }
  }

  fun String.mIndent(indentSize:Int):String{
    val indentStr = " ".repeat(indentSize)
    return this.lineSequence()
      .joinToString("\n") { line ->
        if (line.isBlank()) line else indentStr + line
      }
  }
  fun generateReleaseContextCode(context: GeneratorContext,origin:Int = 0):String{
//    println("generateReleaseContextCode:${context.needRelease},${context.needRelease.lastIndex},${origin}")
    return context.needRelease.subList(origin,context.needRelease.size).joinToString("\n"){
      "$it;"
    }
  }


  fun callMethodCode(method:CommonMethod, context: GeneratorContext):String{
    val staticStr = if(method.isStatic) "Static" else ""
    val obj = if(method.isStatic) "clazz" else "obj"
    if(method.returnType.dimensions>0){
      return """
          |jobject result = (*env)->Call${staticStr}ObjectMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |if(result == NULL){
          |  lua_pushnil(L);
          |}else{
          |  JavaArray* javaArray = lua_newuserdata(L,sizeof(JavaArray));
          |  javaArray->id = luaJniCacheObject(env,result);
          |  (*env)->DeleteLocalRef(env,result);
          |  javaArray->elementType = ${generateArrayElementTypeCode(method.returnType.name)};
          |  luaL_setmetatable(L,"JavaArray");
          |}
      """.trimMargin()
    }
    return when(method.returnType.name){
      "void" -> """
          |(*env)->Call${staticStr}VoidMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushnil(L);
        """.trimMargin()
      "boolean" -> """
          |jboolean result = (*env)->Call${staticStr}BooleanMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushboolean(L,result);
        """.trimMargin()
      "byte" -> """
          |jbyte result = (*env)->Call${staticStr}ByteMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushinteger(L,result);
        """.trimMargin()
      "short" -> """
          |jshort result = (*env)->Call${staticStr}ShortMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushinteger(L,result);
        """.trimMargin()
      "int" -> """
          |jint result = (*env)->Call${staticStr}IntMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushinteger(L,result);
        """.trimMargin()
      "long" -> """
          |jlong result = (*env)->Call${staticStr}LongMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushinteger(L,result);
        """.trimMargin()
      "float" -> """
          |jfloat result = (*env)->Call${staticStr}FloatMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushnumber(L,result);
        """.trimMargin()
      "double" -> """
          |jdouble result = (*env)->Call${staticStr}DoubleMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushnumber(L,result);
        """.trimMargin()
      "java.lang.String" -> """
          |jstring result = (jstring)(*env)->Call${staticStr}ObjectMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |if(result == NULL){
          |  lua_pushnil(L);
          |}else{
          |  const char* str = (*env)->GetStringUTFChars(env,result,NULL);
          |  lua_pushstring(L,str);
          |  (*env)->ReleaseStringUTFChars(env,result,str);
          |  (*env)->DeleteLocalRef(env,result);
          |}
        """.trimMargin()
      else -> """
          |jobject result = (*env)->Call${staticStr}ObjectMethod(env,$obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |if(result == NULL){
          |  lua_pushnil(L);
          |}else{
          |  JavaObject* javaObject = lua_newuserdata(L,sizeof(JavaObject));
          |  javaObject->id = luaJniCacheObject(env,result);
          |  (*env)->DeleteLocalRef(env,result);
          |  luaL_setmetatable(L,"${method.returnType.name}");
          |}
        """.trimMargin()
    }
  }
  fun generateParametersName(parameters:List<CommonParameter>):String{
    return if(parameters.isEmpty()) "" else ","+parameters.joinToString(",") {
      toCParameterName(it.name)
    }
  }

  fun java2luaException(context: GeneratorContext):String{
    return """
      |if(luaJniCatchJavaException(L,env)){
      |${generateReleaseContextCode(context).mIndent(2)}
      |  lua_error(L);
      |}
    """.trimMargin()
  }

  fun GeneratorContext.addPutBackObject(name:String){
    needRelease.add("""
      |if($name != NULL){
      |  luaJniPutBackObject(env,$name);
      |}
    """.trimMargin())
  }

  fun GeneratorContext.addDeleteLocalRef(name:String){
    val code = """
      |if($name != NULL){
      |  (*env)->DeleteLocalRef(env,$name);
      |}
    """.trimMargin()
    needRelease.add(code)
  }

  fun getFieldIdCode(field:CommonField):String{
    return "classInfo->${toCFieldName(field)} = (*env)->GetFieldID(env,clazz,\"${field.name}\",\"${jniParameterType(field.type)}\");"
  }

  fun getMethodIdCode(method:CommonMethod):String{
    val staticStr = if(method.isStatic) "Static" else ""
    return "classInfo->${toCMethodName(method)} = (*env)->Get${staticStr}MethodID(env,clazz,\"${method.name}\",\"${jniMethodType(method)}\");"
  }

  fun jniMethodType(method:CommonMethod):String{
    val returnType = jniParameterType(method.returnType)
    val parameters = method.parameters.joinToString("") {
      jniParameterType(it.type)
    }
    return "($parameters)$returnType"
  }

  private fun jniParameterType(type: CommonType):String{
    val base = when(type.name){
      "void" -> "V"
      "boolean" -> "Z"
      "byte" -> "B"
      "char" -> "C"
      "short" -> "S"
      "int" -> "I"
      "long" -> "J"
      "float" -> "F"
      "double" -> "D"
      else -> "L${type.name.replace(".","/")};"
    }
    return if(type.dimensions == 0) base else "[".repeat(type.dimensions)+base
  }

  fun MetaData.shortName():String{
    val name = className()
    val lastStr = if(name.contains("$")) "$" else "."
    return name.substring(name.lastIndexOf(lastStr)+1)
  }

  fun headerCode(metaData:MetaData):String{
    val defineH = "${metaData.fileName()}_h".uppercase()
    val code = """
    |#ifndef $defineH
    |#define $defineH
    |#ifdef __cplusplus
    |extern "C" {
    |#endif
    |#include<jni.h>
    |int register_${metaData.injectToLuaMethodName()}(JNIEnv*env);
    |int unregister_${metaData.injectToLuaMethodName()}(JNIEnv*env);
    |#ifdef __cplusplus
    |}
    |#endif
    |
    |#endif //$defineH
    """.trimMargin()
    return code
  }



  fun parameterCheckTypeCode(parameterName:String,type:CommonType,context: GeneratorContext,
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
  fun parameterCheckTypeCode(parameter:CommonParameter,
                                     context: GeneratorContext, index:Int):String{
    return parameterCheckTypeCode(parameter.name,parameter.type,context,index)
  }
  fun parameterInitCode(parameterName: String,type:CommonType,
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

  fun parametersInitCode(method: CommonMethod, context: GeneratorContext,indexShift:Int=2,checkJniObjectParameter: Boolean=false):String{
    return method.parameters.withIndex().joinToString("\n"){ (index,parameter) ->
      parameterInitCode(parameter.name,parameter.type,context,index+indexShift,checkJniObjectParameter)
    }
  }
  fun parametersCheckCode(method:CommonMethod,context: GeneratorContext,indexOrigin:Int=2):String{
    return if(method.parameters.isEmpty()) "" else
      method.parameters.withIndex().joinToString("\n"){ (index,parameter) ->
        parameterCheckTypeCode(parameter,context,index+indexOrigin)
      }
  }
  private fun jniObjectParameterName(parameterName:String):String {
    return "jniObj_${parameterName}"
  }

  fun methodCallName(name:String):String{
    return "method_${name}"
  }
  fun setGlobalFunctionCode(method:CommonMethod):String{
    return """
        lua_pushlightuserdata(L,classInfo);
        lua_pushcclosure(L,${methodCallName(method.name)},1);
        lua_setglobal(L,"${method.name}");
      """.trimIndent()
  }


  fun isStaticFunction(method: ExecutableElement):Boolean{
    val enclosingElement = method.enclosingElement
    return  when {
      // 顶层函数
      enclosingElement.kind == ElementKind.PACKAGE -> true
      // companion object 中的函数
      enclosingElement.kind == ElementKind.CLASS &&
              isCompanionObject(enclosingElement as TypeElement) -> true
      // Java static 方法
      method.modifiers.contains(Modifier.STATIC) -> true
      else -> false
    }
  }
  private fun rawIsKotlinObject(typeElement: TypeElement): Boolean {
    // 检查是否有 kotlin.Metadata 注解
    val metadataAnnotation = typeElement.getAnnotation(Metadata::class.java)
    if (metadataAnnotation != null) {
      // 检查 Metadata 注解的 kind 属性
      val kind = metadataAnnotation.javaClass.getMethod("k").invoke(metadataAnnotation) as Int
      // kind == 1 表示这是一个 Kotlin 对象
      return kind == 1
    }
    return false
  }

  fun isKotlinObject(typeElement: TypeElement): Boolean {
    val father = typeElement.enclosingElement
    if(father is TypeElement){
      return !isCompanionObject(father) && rawIsKotlinObject(typeElement)
    }
    return !isCompanionObject(typeElement) && rawIsKotlinObject(typeElement)
  }

  fun isCompanionObject(element: Element): Boolean {
    val metadata: Metadata = element.getAnnotation(Metadata::class.java)
    val data = KotlinClassMetadata.readStrict(metadata)
    if (data is KotlinClassMetadata.Class) {
      val kClass = data.kmClass
      return kClass.companionObject != null
    }
    return false
  }

  fun getJvmName(element: Element): String {
    var qualifiedName = (element as TypeElement).qualifiedName.toString()
    var enclosingElement = element.enclosingElement
    while (enclosingElement is TypeElement) {
      qualifiedName = enclosingElement.qualifiedName.toString() + "$" + qualifiedName.substring(
        qualifiedName.lastIndexOf('.') + 1
      )
      enclosingElement = enclosingElement.enclosingElement
    }
    return qualifiedName
  }
}