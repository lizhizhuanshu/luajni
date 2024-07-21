package top.lizhistudio.annotation.processor

import androidx.privacysandbox.tools.kotlinx.metadata.internal.metadata.deserialization.Flags
import androidx.privacysandbox.tools.kotlinx.metadata.jvm.KotlinClassMetadata
import com.google.auto.service.AutoService
import top.lizhistudio.annotation.LuaClass
import top.lizhistudio.annotation.LuaEnum
import top.lizhistudio.annotation.LuaFunction
import top.lizhistudio.annotation.processor.ClassElementMetaData.Companion.toCommonMethod
import top.lizhistudio.annotation.processor.GenerateUtil.isCompanionObject
import top.lizhistudio.annotation.processor.GenerateUtil.isKotlinObject
import top.lizhistudio.annotation.processor.GenerateUtil.isStaticFunction
import top.lizhistudio.annotation.processor.GenerateUtil.mIndent
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation
import kotlin.coroutines.CoroutineContext


@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("top.lizhistudio.annotation.LuaClass",
  "top.lizhistudio.annotation.LuaEnum",
  "top.lizhistudio.annotation.LuaFunction")
class AnnotationProcessor: AbstractProcessor() {
  private val generators = mutableListOf<Generator>()
  private fun insertFunction(element:ExecutableElement){
    println("insertFunction ${element.simpleName}  enclosing ${element.enclosingElement.simpleName}")
    val enclosing = element.enclosingElement as TypeElement
    val name = enclosing.qualifiedName.toString()
    val old = generators.firstOrNull {it.className() == name}
    val method = toCommonMethod(element)
    if(old != null){
      (old as FunctionContainer).putFunction(method)
    }else{
      val generator = FunctionsCodeGenerator(enclosing)
      generator.putFunction(method)
      generators.add(generator)
    }
  }
  override fun process(p0: MutableSet<out TypeElement>?, p1: RoundEnvironment?): Boolean {

    p1?.getElementsAnnotatedWith(LuaClass::class.java)?.forEach {
      val generator = ClassCodeGenerator(ClassElementMetaData(it as TypeElement))
      generators.add(generator)
    }
    p1?.getElementsAnnotatedWith(LuaEnum::class.java)?.forEach {
      val generator = EnumCodeGenerator(it as TypeElement)
      generators.add(generator)
    }

    p1?.getElementsAnnotatedWith(LuaFunction::class.java)?.filter {
      it.kind == ElementKind.METHOD &&
              it is ExecutableElement &&
              ( isStaticFunction(it) || isKotlinObject(it.enclosingElement as TypeElement))
    }?.forEach {
      insertFunction(it as ExecutableElement)
    }

    if(p1?.processingOver() == true){
      writeCFile()
      writeCMakeLists()
      writeRegisterAllFile()
      writeAutoRegisterFile()
    }
    return true
  }

  private fun writeCFile(){
    val filer = processingEnv.filer
    generators.forEach { generator->
      val fileName = generator.fileName()
      filer.createResource(StandardLocation.SOURCE_OUTPUT,"cpp","$fileName.h").openWriter().use {
        it.write(generator.headerCode())
      }
      filer.createResource(StandardLocation.SOURCE_OUTPUT,"cpp","$fileName.c").openWriter().use {
        it.write(generator.sourceCode())
      }
    }

  }

  private fun writeRegisterAllFile(){

    val header = """
      |#ifndef LUA_JNI_EXTENSION_H
      |#define LUA_JNI_EXTENSION_H
      |#include <jni.h>
      |void luaJniExtensionRegisterAll(JNIEnv * env);
      |void luaJniExtensionUnregisterAll(JNIEnv * env);
      |#endif //LUA_JNI_EXTENSION_H
    """.trimMargin()

    val includeCode = generators.joinToString("\n") {
      "#include \"${it.fileName()}.h\""
    }

    val source = """
      |#include "lua_jni_extension.h"
      |#include "luajni.h"
      |
      |$includeCode
      |
      |void luaJniExtensionRegisterAll(JNIEnv * env)
      |{
      |${generators.joinToString("\n"){ "register_${it.injectToLuaMethodName()}(env);" }.mIndent(2)}
      |}
      |
      |void luaJniExtensionUnregisterAll(JNIEnv * env)
      |{
      |${generators.joinToString("\n"){ "unregister_${it.injectToLuaMethodName()}(env);" }.mIndent(2)}
      |}
    """.trimMargin()

    processingEnv.filer.createResource(StandardLocation.SOURCE_OUTPUT,"cpp","lua_jni_extension.h").openWriter().use {
      it.write(header)
    }

    processingEnv.filer.createResource(StandardLocation.SOURCE_OUTPUT,"cpp","lua_jni_extension.c").openWriter().use {
      it.write(source)
    }
  }

  private fun writeAutoRegisterFile(){
    val code = """
      |#include <jni.h>
      |#include "luajni.h"
      |
      |#include "lua_jni_extension.h"
      |
      |JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void * reserved)
      |{
      |  JNIEnv * env = NULL;
      |  if ((*vm)->GetEnv(vm,(void**)&env, JNI_VERSION_1_6) != JNI_OK)
      |      return -1;
      |  luaJniExtensionRegisterAll(env);
      |  return  JNI_VERSION_1_6;
      |}
      |
      |JNIEXPORT void JNI_OnUnload(JavaVM * vm, void * reserved)
      |{
      |  JNIEnv * env = NULL;
      |  if ((*vm)->GetEnv(vm,(void**)&env, JNI_VERSION_1_6) != JNI_OK)
      |    return;
      |  luaJniExtensionUnregisterAll(env);
      |}
    """.trimMargin()

    processingEnv.filer.createResource(StandardLocation.SOURCE_OUTPUT,"cpp","lua_jni_extension_auto_register.c").openWriter().use {
      it.write(code)
    }
  }

  private  fun writeCMakeLists(){
    val sourceFileNames = generators.map { generator->
      "\"${generator.fileName()}.c\""
    }.joinToString(" \n").mIndent(2)
    val code = """
      |cmake_minimum_required(VERSION 3.10)
      |if(NOT DEFINED LUA_JNI_EXTENSION_NAME)
      |  set(LUA_JNI_EXTENSION_NAME "lua_jni_extension")
      |endif()
      |
      |if(NOT DEFINED LUA_JNI_LIB_NAMES)
      |  set(LUA_JNI_LIB_NAMES "luajni;lua")
      |endif()
      |
      |project(${'$'}{LUA_JNI_EXTENSION_NAME})
      |
      |option(LUA_JNI_SHARED "Build shared library" ON)
      |option(LUA_JNI_AUTO_REGISTER "Auto register" ON)
      |
      |set(LUA_JNI_SRC "lua_jni_extension.c" $sourceFileNames)
      |
      |
      |if(${'$'}{LUA_JNI_AUTO_REGISTER})
      |  list(APPEND LUA_JNI_SRC "lua_jni_extension_auto_register.c")
      |endif()
      |
      |if(${'$'}{LUA_JNI_SHARED})
      |add_library(${'$'}{LUA_JNI_EXTENSION_NAME} SHARED ${'$'}{LUA_JNI_SRC})
      |else()
      |add_library(${'$'}{LUA_JNI_EXTENSION_NAME} STATIC ${'$'}{LUA_JNI_SRC})
      |endif()
      |
      |target_link_libraries(${'$'}{LUA_JNI_EXTENSION_NAME} ${'$'}{LUA_JNI_LIB_NAMES})
      |
    """.trimMargin()
    processingEnv.filer.createResource(StandardLocation.SOURCE_OUTPUT,"cpp","CMakeLists.txt").openWriter().use {
      it.write(code)
    }
  }

  companion object{
    const val TAG = "AnnotationProcessor"



  }
}