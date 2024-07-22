package top.lizhistudio.annotation.processor.data

data class CommonField(val name:String ,
                       val type:CommonType,
                       val readonly:Boolean = false,
                       val static:Boolean = false,
                       val alias:String?=null)

fun CommonField.indexName():String{
  return this.alias ?: this.name
}


