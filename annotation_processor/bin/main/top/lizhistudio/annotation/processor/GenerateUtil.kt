package top.lizhistudio.annotation.processor


import top.lizhistudio.annotation.processor.data.CommonField
import top.lizhistudio.annotation.processor.data.CommonMethod
import top.lizhistudio.annotation.processor.data.CommonParameter
import top.lizhistudio.annotation.processor.data.CommonType
import top.lizhistudio.annotation.processor.data.GeneratorContext

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

  fun generateCallMethodCode(method:CommonMethod,context: GeneratorContext):String{
    if(method.returnType.dimensions>0){
      return """
          |jobject result = (*env)->CallObjectMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |if(result == NULL){
          |  lua_pushnil(L);
          |}else{
          |  JavaArray* javaArray = luaL_newuserdata(L,sizeof(JavaArray));
          |  javaArray->id = luaJniCacheJavaObject(env,result);
          |  (*env)->DeleteLocalRef(env,result);
          |  javaArray->elementType = ${generateArrayElementTypeCode(method.returnType.name)};
          |  luaL_setmetatable(L,"JavaArray");
          |}
      """.trimMargin()
    }
    return when(method.returnType.name){
      "void" -> """
          |(*env)->CallVoidMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushnil(L);
        """.trimMargin()
      "boolean" -> """
          |jboolean result = (*env)->CallBooleanMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushboolean(L,result);
        """.trimMargin()
      "byte" -> """
          |jbyte result = (*env)->CallByteMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushinteger(L,result);
        """.trimMargin()
      "short" -> """
          |jshort result = (*env)->CallShortMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushinteger(L,result);
        """.trimMargin()
      "int" -> """
          |jint result = (*env)->CallIntMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushinteger(L,result);
        """.trimMargin()
      "long" -> """
          |jlong result = (*env)->CallLongMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushinteger(L,result);
        """.trimMargin()
      "float" -> """
          |jfloat result = (*env)->CallFloatMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushnumber(L,result);
        """.trimMargin()
      "double" -> """
          |jdouble result = (*env)->CallDoubleMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |lua_pushnumber(L,result);
        """.trimMargin()
      "java.lang.String" -> """
          |jstring result = (jstring)(*env)->CallObjectMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
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
          |jobject result = (*env)->CallObjectMethod(env,obj,classInfo->${toCMethodName(method)}${generateParametersName(method.parameters)});
          |${java2luaException(context)}
          |if(result == NULL){
          |  lua_pushnil(L);
          |}else{
          |  JavaObject* javaObject = luaL_newuserdata(L,sizeof(JavaObject));
          |  javaObject->id = luaJniCacheJavaObject(env,result);
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
    return "classInfo->${toCMethodName(method)} = (*env)->GetMethodID(env,clazz,\"${method.name}\",\"${jniMethodType(method)}\");"
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

  fun MetaData.shortName() = className().substringAfterLast(".")

}