package top.lizhistudio.annotation.processor

import top.lizhistudio.annotation.processor.data.CommonField
import top.lizhistudio.annotation.processor.data.CommonMethod

interface ClassMetaData:MetaData {
  fun autoRegister():Boolean
  fun fields():List<CommonField>
  fun methods():List<CommonMethod>
  fun constructors():List<CommonMethod>
}