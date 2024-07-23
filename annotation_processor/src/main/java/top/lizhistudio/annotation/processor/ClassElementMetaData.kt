package top.lizhistudio.annotation.processor


import top.lizhistudio.annotation.LuaClass
import top.lizhistudio.annotation.LuaField
import top.lizhistudio.annotation.LuaFunction
import top.lizhistudio.annotation.processor.GenerateUtil.capitalizeFirstLetter
import top.lizhistudio.annotation.processor.GenerateUtil.getJvmName
import top.lizhistudio.annotation.processor.GenerateUtil.isStaticField
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
          methods.add(toCommonMethodWithLuaField(it as ExecutableElement))
        }
        ElementKind.FIELD -> {
          fields.add(toCommonField(it as VariableElement))
        }
        ElementKind.CONSTRUCTOR -> {
          constructors.add(toCommonMethodWithLuaField(it as ExecutableElement))
        }
        else -> {}
      }
    }
    if(clazz.getAnnotation(Metadata::class.java) != null){
      fields.forEach {
        methods.add(toGetterMethod(it))
        if(!it.readonly) methods.add(toSetterMethod(it))
      }
      fields.clear()
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
      val name = element.simpleName.toString()
      val alias = if(annotation?.alias.isNullOrEmpty()) name else annotation!!.alias
      val type = element.asType()
      val readonly = annotation?.readonly ?: false || element.modifiers.contains(Modifier.FINAL)
      return CommonField(name, toCommonType(type),readonly = readonly,static = isStaticField(element),alias)
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

    fun toCommonMethodWithLuaField(element:ExecutableElement):CommonMethod{
      val annotation = element.getAnnotation(LuaField::class.java)
        ?: throw IllegalArgumentException("element is not annotated with LuaField")
      val name = element.simpleName.toString()
      val alias = annotation.alias.ifEmpty { name }
      val returnType = element.returnType
      val parameters = element.parameters.map { toCommonParameter(it) }
      val unpack = if(annotation.unpack.isEmpty()) null else annotation.unpack
      return CommonMethod(name,
        toCommonType(returnType),
        parameters,
        annotation.method2field,
        isStaticFunction(element),
        alias,
        unpack)
    }

    fun toCommonMethodWithLuaFunction(element:ExecutableElement):CommonMethod{
      val annotation = element.getAnnotation(LuaFunction::class.java)
        ?: throw IllegalArgumentException("element is not annotated with LuaFunction")
      val name = element.simpleName.toString()
      val alias = annotation.alias.ifEmpty { name }
      val unpack = if(annotation.unpack.isEmpty()) null else annotation.unpack
      val returnType = element.returnType
      val parameters = element.parameters.map { toCommonParameter(it) }
      return CommonMethod(name,
        toCommonType(returnType),
        parameters,
        false,
        isStaticFunction(element),
        alias,
        unpack)
    }

    fun toCommonParameter(element:VariableElement): CommonParameter {
      val name = element.simpleName.toString()
      val type = element.asType()
      return CommonParameter(name, toCommonType(type))
    }

    private fun toGetterName(name:String):String{
      return "get${name.capitalizeFirstLetter()}"
    }
    private fun toSetterName(name:String):String{
      return "set${name.capitalizeFirstLetter()}"
    }
    private fun toGetterMethod(field:CommonField):CommonMethod{
      return CommonMethod(toGetterName(field.name),
        field.type,emptyList(),
        true,
        field.static,
        field.alias)
    }
    private fun toSetterMethod(field:CommonField):CommonMethod{
      return CommonMethod(toSetterName(field.name),
        CommonType("void",0),
        listOf(CommonParameter("value",field.type)),
        true,
        field.static,field.alias)
    }
  }
}