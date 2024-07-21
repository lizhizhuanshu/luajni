package top.lizhistudio.annotation.processor


import top.lizhistudio.annotation.processor.ClassMetaData
import top.lizhistudio.annotation.LuaClass
import top.lizhistudio.annotation.LuaField
import top.lizhistudio.annotation.processor.GenerateUtil.getJvmName
import top.lizhistudio.annotation.processor.GenerateUtil.isStaticFunction
import top.lizhistudio.annotation.processor.data.CommonField
import top.lizhistudio.annotation.processor.data.CommonMethod
import top.lizhistudio.annotation.processor.data.CommonParameter
import top.lizhistudio.annotation.processor.data.CommonType
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

class ClassElementMetaData(private val clazz:TypeElement): ClassMetaData {
  private val fields = mutableListOf<CommonField>()
  private val methods = mutableListOf<CommonMethod>()
  private val constructors = mutableListOf<CommonMethod>()
  init {
    clazz.enclosedElements.filter {it.getAnnotation(LuaField::class.java)!=null}.forEach {
      when (it.kind) {
        ElementKind.METHOD -> {
          methods.add(toCommonMethod(it as ExecutableElement))
        }
        ElementKind.FIELD -> {
          fields.add(toCommonField(it as VariableElement))
        }
        ElementKind.CONSTRUCTOR -> {
          constructors.add(toCommonMethod(it as ExecutableElement))
        }
        else -> {}
      }
    }

  }
  override fun autoRegister(): Boolean {
    return clazz.getAnnotation(LuaClass::class.java).autoRegister
  }

  override fun fields(): List<CommonField> {
    return fields
  }

  override fun methods(): List<CommonMethod> {
    return methods
  }

  override fun constructors(): List<CommonMethod> {
    return constructors
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

  companion object {
    fun toCommonField(element:VariableElement):CommonField{
      val annotation = element.getAnnotation(LuaField::class.java)
      val name = if(annotation.alias.isEmpty()) element.simpleName.toString() else annotation.alias
      val type = element.asType()
      val readonly = annotation.readonly || element.modifiers.contains(Modifier.FINAL)
      return CommonField(name, toCommonType(type),readonly = readonly)
    }
    fun toCommonType(type:TypeMirror):CommonType{
      var t = type
      var count = 0
      while(t.kind == TypeKind.ARRAY){
        t = (t as javax.lang.model.type.ArrayType).componentType
        count++
      }
      return CommonType(t.toString(),count)
    }
    fun toCommonMethod(element:ExecutableElement):CommonMethod{
      val annotation = element.getAnnotation(LuaField::class.java)
      val name = if(annotation == null || annotation.alias.isEmpty()) element.simpleName.toString() else annotation.alias
      val returnType = element.returnType
      val parameters = element.parameters.map { toCommonParameter(it) }
      return CommonMethod(name, toCommonType(returnType),parameters,annotation?.method2field?:false,isStaticFunction(element))
    }
    fun toCommonParameter(element:VariableElement): CommonParameter {
      val name = element.simpleName.toString()
      val type = element.asType()
      return CommonParameter(name, toCommonType(type))
    }
  }
}