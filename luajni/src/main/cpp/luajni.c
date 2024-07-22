//
// Created by lizhi on 2024/7/8.
//
#include "luajni.h"

#include <stdio.h>
#include <stdlib.h>

#include <lauxlib.h>
#include <string.h>

#include "mlog.h"

#define JAVA_ARRAY_META_NAME "JavaArray"
#define TABLE_SIZE 100
#define PUSH_THROWABLE_ERROR "push java throwable error"


typedef struct Value{
    LuaJniInjectMethod method;
    void* userData;
}Value;

typedef struct KeyValue{
    char* key;
    Value value;
    struct KeyValue *next;
}KeyValue;

typedef struct HashMap{
    KeyValue *table[TABLE_SIZE];
}HashMap;

typedef struct Context{
    HashMap map;

    jclass booleanClass;
    jclass byteClass;
    jclass charClass;
    jclass shortClass;
    jclass intClass;
    jclass longClass;
    jclass floatClass;
    jclass doubleClass;

    jmethodID newBoolean;
    jmethodID newByte;
    jmethodID newChar;
    jmethodID newShort;
    jmethodID newInt;
    jmethodID newLong;
    jmethodID newFloat;
    jmethodID newDouble;

    jmethodID booleanValue;
    jmethodID byteValue;
    jmethodID charValue;
    jmethodID shortValue;
    jmethodID intValue;
    jmethodID longValue;
    jmethodID floatValue;
    jmethodID doubleValue;
}Context;


unsigned int hashMapHash(const char*key);
void hashMapPut(HashMap*map, const char*key, LuaJniInjectMethod method, void*userData);
Value* hashMapGet(HashMap*map, const char*key);
void* hashMapRemove(HashMap*map, const char*key);
void hashMapClear(HashMap*map);
HashMap *ensureHashMap();

static Context * context = NULL;



int luaJniEqualJavaArray(JavaArray* a, const char*className, int level, enum ARRAY_ELEMENT_TYPE elementType){
    return a != NULL &&
    a->level == level &&
    a->elementType == elementType &&
    ((a->name == NULL && className == NULL) || (a->name != NULL && className != NULL && strcmp(a->name,className) == 0));
}


int64_t luaJniCacheObject(JNIEnv*env, jobject obj){
    jobject globalRef = (*env)->NewGlobalRef(env,obj);
    return (int64_t)globalRef;
}

void luaJniReleaseObject(JNIEnv*env, int64_t id){
    jobject obj = (jobject)id;
    (*env)->DeleteGlobalRef(env,obj);
}


int luaJniJavaObjectGc(lua_State *L){
    JavaObject *object = (JavaObject *) lua_touserdata(L,1);
    JNIEnv *env = luaJniGetEnv(L);
    lua_getmetatable(L,1);
    lua_getfield(L,-1,"__name");
    const char *name = lua_tostring(L,-1);
    LOGD("name %s env %p \n",name,env);
    LOGD("delete global ref %p \n",luaJniTakeObject(env,object->id));
    (*env)->DeleteGlobalRef(env, luaJniTakeObject(env,object->id));
    return 0;
}

static int pushJavaThrowable(JNIEnv* env,lua_State*L,jthrowable throwable)
{
    jclass clazz = (*env)->FindClass(env,"java/lang/Throwable");
    jmethodID method = (*env)->GetMethodID(env,clazz,"getMessage", "()Ljava/lang/String;");
    jstring message = (jstring)(*env)->CallObjectMethod(env,throwable,method);
    if (!(*env)->ExceptionCheck(env))
    {
        const char *cMessage = (*env)->GetStringUTFChars(env,message,0);
        lua_pushstring(L,cMessage);
        (*env)->ReleaseStringUTFChars(env,message,cMessage);
        return 1;
    }
    (*env)->ExceptionDescribe(env);
    (*env)->ExceptionClear(env);
    return 0;
}

int luaJniCatchJavaException(lua_State*L, JNIEnv*env){
    jthrowable throwable = (*env)->ExceptionOccurred(env);
    if(throwable){
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        if(!pushJavaThrowable(env,L,throwable)){
            lua_pushstring(L,PUSH_THROWABLE_ERROR);
        }
        return 1;
    }
    return 0;
}

void luaJniCatchJavaAndThrowLuaException(lua_State*L, JNIEnv*env){
    if(luaJniCatchJavaException(L,env)){
        lua_error(L);
    }
}


static int javaArrayIndex(lua_State*L){
    JavaArray *array = (JavaArray *) luaL_checkudata(L,1,JAVA_ARRAY_META_NAME);
    jint index = luaL_checkint(L,2);
    JNIEnv *env = luaJniGetEnv(L);
    jobject obj = luaJniTakeObject(env,array->id);
    jint length = (*env)->GetArrayLength(env,obj);
    if(index <1 || index > length){
        luaJniPutBackObject(env,obj);
        luaL_error(L,"index out of range %d",index);
    }
    if(array->level >1){
        jarray nArray = (*env)->GetObjectArrayElement(env,obj,index-1);
        if(luaJniCatchJavaException(L, env)){
            luaJniPutBackObject(env,obj);
            lua_error(L);
        }
        if(nArray){
            JavaArray *newArray = (JavaArray *) lua_newuserdata(L,sizeof(JavaArray));
            newArray->id = luaJniCacheObject(env, nArray);
            newArray->level = array->level -1;
            newArray->name = array->name;
            luaL_getmetatable(L,JAVA_ARRAY_META_NAME);
            lua_setmetatable(L,-2);
        }else{
            lua_pushnil(L);
        }
    }else{
        switch (array->elementType){
            case ELEMENT_BOOLEAN: {
                jboolean value;
                (*env)->GetBooleanArrayRegion(env,obj,index-1,1,&value);
                lua_pushboolean(L, value);
                break;
            }
            case ELEMENT_BYTE:{
                jbyte value;
                (*env)->GetByteArrayRegion(env,obj,index-1,1,&value);
                lua_pushinteger(L,value);
                break;
            }
            case ELEMENT_CHAR:{
                jchar value;
                (*env)->GetCharArrayRegion(env,obj,index-1,1,&value);
                lua_pushinteger(L,value);
                break;
            }
            case ELEMENT_SHORT: {
                jshort value;
                (*env)->GetShortArrayRegion(env,obj,index-1,1,&value);
                lua_pushinteger(L,value);
                break;
            }
            case ELEMENT_INT: {
                jint value;
                (*env)->GetIntArrayRegion(env, obj, index - 1, 1, &value);
                lua_pushinteger(L, value);
                break;
            }
            case ELEMENT_LONG:{
                jlong value;
                (*env)->GetLongArrayRegion(env,obj,index-1,1,&value);
                lua_pushinteger(L,value);
                break;
            }
            case ELEMENT_FLOAT:{
                jfloat value;
                (*env)->GetFloatArrayRegion(env,obj,index-1,1,&value);
                lua_pushnumber(L,value);
                break;
            }
            case ELEMENT_DOUBLE:{
                jdouble value;
                (*env)->GetDoubleArrayRegion(env,obj,index-1,1,&value);
                lua_pushnumber(L,value);
                break;
            }
            case ELEMENT_STRING:{
                jstring value = (*env)->GetObjectArrayElement(env,obj,index-1);
                if(value != NULL){
                    const char *str = (*env)->GetStringUTFChars(env,value,0);
                    lua_pushstring(L,str);
                    (*env)->ReleaseStringUTFChars(env,value,str);
                    (*env)->DeleteLocalRef(env,value);
                }else{
                    lua_pushnil(L);
                }
                break;
            }
            case ELEMENT_OBJECT: {
                jobject value = (*env)->GetObjectArrayElement(env, obj, index - 1);
                if (value != NULL) {
                    int64_t id = luaJniCacheObject(env, value);
                    JavaObject *object = (JavaObject *) lua_newuserdata(L, sizeof(JavaObject));
                    object->id = id;
                    (*env)->DeleteLocalRef(env,value);
                    int r = luaL_getmetatable(L, array->name);
                    if (r) {
                        lua_setmetatable(L, -2);
                    } else {
                        luaJniReleaseObject(env, id);
                        luaJniPutBackObject(env,obj);
                        luaL_error(L, "can not find metatable for %s", array->name);
                    }
                } else {
                    lua_pushnil(L);
                }
                break;
            }
        }
    }
    luaJniPutBackObject(env,obj);
    return 1;
}
static int javaArrayNewIndex(lua_State*L){
    JavaArray *array = (JavaArray *) luaL_checkudata(L,1,JAVA_ARRAY_META_NAME);
    jint index = luaL_checkint(L,2);
    JNIEnv *env = luaJniGetEnv(L);
    jobject obj = luaJniTakeObject(env,array->id);
    jint length = (*env)->GetArrayLength(env,obj);
    if(index <1 || index > length){
        luaJniPutBackObject(env,obj);
        luaL_error(L,"index out of range %d",index);
    }
    if(array->level>1){
        jobject value = NULL;
        if(!lua_isnil(L,3)){
            JavaArray *element = (JavaArray *) luaL_testudata(L,3,JAVA_ARRAY_META_NAME);
            if(element == NULL){
                luaJniPutBackObject(env,obj);
                luaL_error(L,"expect java array");
            }
            value = luaJniTakeObject(env,element->id);
        }
        (*env)->SetObjectArrayElement(env,obj,index-1,value);
        if(luaJniCatchJavaException(L, env)){
            luaJniPutBackObject(env,obj);
            if(value){
                luaJniPutBackObject(env,value);
            }
            lua_error(L);
        }
        if(value){
            luaJniPutBackObject(env,value);
        }
    }
    else{
        int luaValueType = lua_type(L,3);
        switch (array->elementType)  {
            case ELEMENT_BOOLEAN:{
                if(luaValueType != LUA_TBOOLEAN){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect boolean");
                }
                jboolean value = lua_toboolean(L,3);
                (*env)->SetBooleanArrayRegion(env,obj,index-1,1,&value);
                break;
            }
            case ELEMENT_BYTE:{
                if(luaValueType != LUA_TNUMBER || !lua_isinteger(L,3)){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect number");
                }
                jbyte value = lua_tointeger(L,3);
                (*env)->SetByteArrayRegion(env,obj,index-1,1,&value);
                break;
            }
            case ELEMENT_CHAR:{
                if(luaValueType != LUA_TNUMBER || !lua_isinteger(L,3)){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect number");
                }
                jchar value = lua_tointeger(L,3);
                (*env)->SetCharArrayRegion(env,obj,index-1,1,&value);
                break;
            }
            case ELEMENT_SHORT:{
                if(luaValueType != LUA_TNUMBER || !lua_isinteger(L,3)){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect number");
                }
                jshort value = lua_tointeger(L,3);
                (*env)->SetShortArrayRegion(env,obj,index-1,1,&value);
                break;
            }
            case ELEMENT_INT:{
                if(luaValueType != LUA_TNUMBER || !lua_isinteger(L,3)){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect number");
                }
                jint value = lua_tointeger(L,3);
                (*env)->SetIntArrayRegion(env,obj,index-1,1,&value);
                break;
            }
            case ELEMENT_LONG:{
                if(luaValueType != LUA_TNUMBER || !lua_isinteger(L,3)){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect number");
                }
                jlong value = lua_tointeger(L,3);
                (*env)->SetLongArrayRegion(env,obj,index-1,1,&value);
                break;
            }
            case ELEMENT_FLOAT:{
                if(luaValueType != LUA_TNUMBER){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect number");
                }
                jfloat value = lua_tonumber(L,3);
                (*env)->SetFloatArrayRegion(env,obj,index-1,1,&value);
                break;
            }
            case ELEMENT_DOUBLE:{
                if(luaValueType != LUA_TNUMBER){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect number");
                }
                jdouble value = lua_tonumber(L,3);
                (*env)->SetDoubleArrayRegion(env,obj,index-1,1,&value);
                break;
            }
            case ELEMENT_STRING:{
                if(luaValueType != LUA_TSTRING){
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect string");
                }
                const char *str = lua_tostring(L,3);
                jstring value = (*env)->NewStringUTF(env,str);
                (*env)->SetObjectArrayElement(env,obj,index-1,value);
                (*env)->DeleteLocalRef(env,value);
                break;
            }
            case ELEMENT_OBJECT:{
                jobject value = NULL;
                if(luaValueType == LUA_TUSERDATA){
                    JavaObject *element = (JavaObject *) luaL_testudata(L,3,array->name);
                    if(element == NULL){
                        luaJniPutBackObject(env,obj);
                        luaL_error(L,"expect java object %s",array->name);
                    }
                    value = luaJniTakeObject(env,element->id);
                }else if(luaValueType == LUA_TNIL) {
                    value = NULL;
                }else {
                    luaJniPutBackObject(env,obj);
                    luaL_error(L,"expect java object %s",array->name);
                }
                (*env)->SetObjectArrayElement(env,obj,index-1,value);
                if(luaJniCatchJavaException(L, env)){
                    luaJniPutBackObject(env,obj);
                    if(value){
                        luaJniPutBackObject(env,value);
                    }
                    lua_error(L);
                }
                if(value){
                    luaJniPutBackObject(env,value);
                }
                break;
            }
        }
    }
    luaJniPutBackObject(env,obj);
    return 0;

}
static int javaArrayLen(lua_State*L){
    JavaArray *array = (JavaArray *) luaL_checkudata(L,1,JAVA_ARRAY_META_NAME);
    JNIEnv *env = luaJniGetEnv(L);
    jobject obj = luaJniTakeObject(env,array->id);
    jint length = (*env)->GetArrayLength(env,obj);
    if(luaJniCatchJavaException(L, env)){
        luaJniPutBackObject(env,obj);
        lua_error(L);
    }
    lua_pushinteger(L,length);
    luaJniPutBackObject(env,obj);
    return 1;
}

void luaJniInitLua(lua_State *L, JNIEnv *env) {
    if(luaL_newmetatable(L,JAVA_ARRAY_META_NAME)){
        luaL_Reg methods[] = {
            {"__index",    javaArrayIndex},
            {"__newindex", javaArrayNewIndex},
            {"__gc",       luaJniJavaObjectGc},
            {"__len",      javaArrayLen},
            {NULL,NULL}
        };
        luaL_setfuncs(L,methods,0);
    }
    lua_pop(L,1);
}

int luaJniInject(lua_State *L, JNIEnv *env,const char*name) {
    HashMap *map = ensureHashMap();
    Value *value = hashMapGet(map, name);
    if(value){
        value->method(L,env,value->userData);
        return 1;
    }
    return 0;
}

int luaJniInjectAll(lua_State *L, JNIEnv *env) {
    HashMap *map = ensureHashMap();
    int count = 0;
    for(int i = 0; i < TABLE_SIZE; i++){
        KeyValue *node = map->table[i];
        while(node){
            count ++;
            node->value.method(L,env,node->value.userData);
            node = node->next;
        }
    }
    return count;
}

int luaJniRegisteredCount(){
    HashMap *map = ensureHashMap();
    int count = 0;
    for(int i = 0; i < TABLE_SIZE; i++){
        KeyValue *node = map->table[i];
        while(node){
            count ++;
            node = node->next;
        }
    }
    return count;
}

#define LUA_JNI_PUSH_BASE_FIELD(javaUpType,javaDownType,luaType,type,staticStr)\
int luaJniPush##staticStr##javaUpType##Field(lua_State*L, JNIEnv *env, j##type a_##type,jfieldID field){\
    j##javaDownType value = (*env)->Get##staticStr##javaUpType##Field(env,a_##type,field);\
    if(luaJniCatchJavaException(L, env)) return 0;\
    lua_push##luaType(L,value);\
    return 1;\
}

#define LUA_JNI_PUSH_WRAPPER(returnName,javaName,luaName,type,staticStr) \
int luaJniPush##staticStr##Wrapper##returnName##Field(lua_State*L, JNIEnv *env, j##type a_##type,jfieldID field){\
    jobject value = (*env)->Get##staticStr##ObjectField(env,a_##type,field);\
    if(luaJniCatchJavaException(L, env)) return 0;\
    if(value != NULL){\
        j##javaName result = (*env)->Call##returnName##Method(env,value,context->javaName##Value);\
        if(luaJniCatchJavaException(L, env)){\
            (*env)->DeleteLocalRef(env,value);\
            return 0;\
        }\
        lua_push##luaName(L,result);\
        (*env)->DeleteLocalRef(env,value);\
    }else{\
        lua_pushnil(L);\
    }\
    return 1;\
}
#define LUA_JNI_PUSH_WRAPPER_X(javaUpType,javaDownType,luaType) \
LUA_JNI_PUSH_WRAPPER(javaUpType,javaDownType,luaType,object,);\
LUA_JNI_PUSH_WRAPPER(javaUpType,javaDownType,luaType,class,Static);

#define LUA_JNI_PUSH_BASE_FIELD_X(javaUpType,javaDownType,luaType) \
LUA_JNI_PUSH_BASE_FIELD(javaUpType,javaDownType,luaType,object,);  \
LUA_JNI_PUSH_BASE_FIELD(javaUpType,javaDownType,luaType,class,Static);

#define LUA_JNI_PUSH_BASE_FIELD_EX(javaUpType,javaDownType,luaType) \
LUA_JNI_PUSH_BASE_FIELD_X(javaUpType,javaDownType,luaType);   \
LUA_JNI_PUSH_WRAPPER_X(javaUpType,javaDownType,luaType);


LUA_JNI_PUSH_BASE_FIELD_EX(Int,int,integer)
LUA_JNI_PUSH_BASE_FIELD_EX(Long,long,integer)
LUA_JNI_PUSH_BASE_FIELD_EX(Short,short,integer)
LUA_JNI_PUSH_BASE_FIELD_EX(Byte,byte,integer)
LUA_JNI_PUSH_BASE_FIELD_EX(Boolean,boolean,boolean)
LUA_JNI_PUSH_BASE_FIELD_EX(Float,float,number)
LUA_JNI_PUSH_BASE_FIELD_EX(Double,double,number)
LUA_JNI_PUSH_BASE_FIELD_EX(Char,char,integer)

#undef LUA_JNI_PUSH_BASE_FIELD_EX
#undef LUA_JNI_PUSH_BASE_FIELD_X
#undef LUA_JNI_PUSH_WRAPPER_X
#undef LUA_JNI_PUSH_WRAPPER
#undef LUA_JNI_PUSH_BASE_FIELD

#define LUA_JNI_PUSH_STRING_FIELD(type,staticStr)\
int luaJniPush##staticStr##StringField(lua_State*L, JNIEnv *env, j##type a_##type,jfieldID field){\
    jstring value = (*env)->Get##staticStr##ObjectField(env,a_##type,field);\
    if(luaJniCatchJavaException(L, env)) return 0;\
    if(value != NULL){\
        const char *str = (*env)->GetStringUTFChars(env,value,0);\
        lua_pushstring(L,str);\
        (*env)->ReleaseStringUTFChars(env,value,str);\
        (*env)->DeleteLocalRef(env,value);\
    }else{\
        lua_pushnil(L);\
    }\
    return 1;\
}

LUA_JNI_PUSH_STRING_FIELD(object,)
LUA_JNI_PUSH_STRING_FIELD(class,Static)
#undef LUA_JNI_PUSH_STRING_FIELD

#define LUA_JNI_PUSH_OBJECT_FIELD(type,staticStr)\
int luaJniPush##staticStr##ObjectField(lua_State*L,JNIEnv*env,j##type a_##type,jfieldID field,const char*className){\
    jobject value = (*env)->Get##staticStr##ObjectField(env,a_##type,field);\
    if(luaJniCatchJavaException(L, env)) return 0;\
    if(value != NULL){\
        int64_t id = luaJniCacheObject(env, value);\
        (*env)->DeleteLocalRef(env,value);\
        JavaObject *object = (JavaObject *) lua_newuserdata(L,sizeof(JavaObject));\
        object->id = id;\
        int r = luaL_getmetatable(L,className);\
        if(r){\
            lua_setmetatable(L,-2);\
        }else{\
            lua_pushfstring(L,"can not find metatable for %s",className);\
            return 0;\
        }\
    }else{\
        lua_pushnil(L);\
    }\
    return 1;\
}

LUA_JNI_PUSH_OBJECT_FIELD(object,)
LUA_JNI_PUSH_OBJECT_FIELD(class,Static)
#undef LUA_JNI_PUSH_OBJECT_FIELD


#define LUA_JNI_PUSH_ARRAY_FIELD(type,staticStr)\
int luaJniPush##staticStr##ArrayField(lua_State*L,JNIEnv*env,j##type a_##type,jfieldID field,const char*className,int level,\
                   enum ARRAY_ELEMENT_TYPE elementType){\
    jobjectArray value = (*env)->Get##staticStr##ObjectField(env,a_##type,field);\
    if(luaJniCatchJavaException(L, env)) return 0;\
    if(value != NULL){\
        JavaArray *array = (JavaArray *) lua_newuserdata(L,sizeof(JavaArray));\
        array->id = (int64_t)value;\
        array->level = level;\
        array->name = className;\
        array->elementType = elementType;\
        luaL_getmetatable(L,JAVA_ARRAY_META_NAME);\
        lua_setmetatable(L,-2);\
    }else{\
        lua_pushnil(L);\
    }\
    return 1;\
}
LUA_JNI_PUSH_ARRAY_FIELD(object,)
LUA_JNI_PUSH_ARRAY_FIELD(class,Static)
#undef LUA_JNI_PUSH_ARRAY_FIELD



unsigned int hashMapHash(const char *key) {
    unsigned int hash = 0;
    while (*key) {
        hash = hash * 31 + *key++;
    }
    return hash % TABLE_SIZE;
}

void hashMapPut(HashMap *map, const char *key, LuaJniInjectMethod method, void *userData) {
    unsigned int index = hashMapHash(key);
    KeyValue *node = map->table[index];
    while (node) {
        if (strcmp(node->key, key) == 0) {
            node->value.method = method;
            node->value.userData = userData;
            return;
        }
        node = node->next;
    }
    node = (KeyValue *) malloc(sizeof(KeyValue));
    node->key = strdup(key);
    node->value.method = method;
    node->value.userData = userData;
    node->next = map->table[index];
    map->table[index] = node;
}

Value *hashMapGet(HashMap *map, const char *key) {
    unsigned int index = hashMapHash(key);
    KeyValue *node = map->table[index];
    while (node) {
        if (strcmp(node->key, key) == 0) {
            return &node->value;
        }
        node = node->next;
    }
    return NULL;
}

void* hashMapRemove(HashMap *map, const char *key) {
    unsigned int index = hashMapHash(key);
    KeyValue *node = map->table[index];
    KeyValue *pre = NULL;
    while (node) {
        if (strcmp(node->key, key) == 0) {
            if (pre) {
                pre->next = node->next;
            } else {
                map->table[index] = node->next;
            }
            void *r = node->value.userData;
            free(node->key);
            free(node);
            return r;
        }
        pre = node;
        node = node->next;
    }
    return NULL;
}

void hashMapClear(HashMap *map) {
    for (int i = 0; i < TABLE_SIZE; i++) {
        KeyValue *node = map->table[i];
        while (node) {
            KeyValue *next = node->next;
            free(node->key);
            free(node);
            node = next;
        }
        map->table[i] = NULL;
    }
}



HashMap *ensureHashMap() {
    return &context->map;
}


int luaJniRegister(const char *name, LuaJniInjectMethod method, void *userData) {
    HashMap *map = ensureHashMap();
    LOGD("register %s",name);
    hashMapPut(map, name, method, userData);
    return 1;
}

void* luaJniUnregister(const char *name) {
    HashMap *map = ensureHashMap();
    return hashMapRemove(map, name);
}

JNIEnv* luaJniGetEnv(lua_State *L) {
    JNIEnv **envPtr = (JNIEnv **) lua_getextraspace(L);
    return *envPtr;
}



int luaJniInitContext(JNIEnv *env) {
    Context *ctx = (Context*) malloc(sizeof(Context));
    memset(ctx, 0, sizeof(Context));
    for (int i = 0; i < TABLE_SIZE; ++i) {
        ctx->map.table[i] = NULL;
    }
    jclass clazz = (*env)->FindClass(env,"java/lang/Boolean");
    ctx->booleanClass = (*env)->NewWeakGlobalRef(env,clazz);

    ctx->newBoolean = (*env)->GetMethodID(env,clazz,"<init>", "(Z)V");
    ctx->booleanValue = (*env)->GetMethodID(env,clazz,"booleanValue", "()Z");
    (*env)->DeleteLocalRef(env,clazz);
    clazz = (*env)->FindClass(env,"java/lang/Byte");
    ctx->byteClass = (*env)->NewWeakGlobalRef(env,clazz);
    ctx->newByte = (*env)->GetMethodID(env,clazz,"<init>", "(B)V");
    ctx->byteValue = (*env)->GetMethodID(env,clazz,"byteValue", "()B");
    (*env)->DeleteLocalRef(env,clazz);
    clazz = (*env)->FindClass(env,"java/lang/Character");
    ctx->charClass = (*env)->NewWeakGlobalRef(env,clazz);
    ctx->newChar = (*env)->GetMethodID(env,clazz,"<init>", "(C)V");
    ctx->charValue = (*env)->GetMethodID(env,clazz,"charValue", "()C");
    (*env)->DeleteLocalRef(env,clazz);
    clazz = (*env)->FindClass(env,"java/lang/Short");
    ctx->shortClass = (*env)->NewWeakGlobalRef(env,clazz);
    ctx->newShort = (*env)->GetMethodID(env,clazz,"<init>", "(S)V");
    ctx->shortValue = (*env)->GetMethodID(env,clazz,"shortValue", "()S");
    (*env)->DeleteLocalRef(env,clazz);
    clazz = (*env)->FindClass(env,"java/lang/Integer");
    ctx->intClass = (*env)->NewWeakGlobalRef(env,clazz);
    ctx->newInt = (*env)->GetMethodID(env,clazz,"<init>", "(I)V");
    ctx->intValue = (*env)->GetMethodID(env,clazz,"intValue", "()I");
    (*env)->DeleteLocalRef(env,clazz);
    clazz = (*env)->FindClass(env,"java/lang/Long");
    ctx->longClass = (*env)->NewWeakGlobalRef(env,clazz);
    ctx->newLong = (*env)->GetMethodID(env,clazz,"<init>", "(J)V");
    ctx->longValue = (*env)->GetMethodID(env,clazz,"longValue", "()J");
    (*env)->DeleteLocalRef(env,clazz);
    clazz = (*env)->FindClass(env,"java/lang/Float");
    ctx->floatClass = (*env)->NewWeakGlobalRef(env,clazz);
    ctx->newFloat = (*env)->GetMethodID(env,clazz,"<init>", "(F)V");
    ctx->floatValue = (*env)->GetMethodID(env,clazz,"floatValue", "()F");
    (*env)->DeleteLocalRef(env,clazz);
    clazz = (*env)->FindClass(env,"java/lang/Double");
    ctx->doubleClass =(*env)->NewWeakGlobalRef(env,clazz);
    ctx->newDouble = (*env)->GetMethodID(env,clazz,"<init>", "(D)V");
    ctx->doubleValue = (*env)->GetMethodID(env,clazz,"doubleValue", "()D");
    (*env)->DeleteLocalRef(env,clazz);
    context = ctx;
    return 1;
}

int luaJniReleaseContext(JNIEnv *env) {
    if (context) {
        hashMapClear(&context->map);
        (*env)->DeleteWeakGlobalRef(env,context->booleanClass);
        (*env)->DeleteWeakGlobalRef(env,context->byteClass);
        (*env)->DeleteWeakGlobalRef(env,context->charClass);
        (*env)->DeleteWeakGlobalRef(env,context->shortClass);
        (*env)->DeleteWeakGlobalRef(env,context->intClass);
        (*env)->DeleteWeakGlobalRef(env,context->longClass);
        (*env)->DeleteWeakGlobalRef(env,context->floatClass);
        (*env)->DeleteWeakGlobalRef(env,context->doubleClass);
        free(context);
        context = NULL;
    }
    return 1;
}

#define LUA_JNI_NEW_WRAPPER(up,down) \
jobject luaJniNew##up(JNIEnv*env, j##down value){\
    return (*env)->NewObject(env,context->down##Class, context->new##up,value);\
}

LUA_JNI_NEW_WRAPPER(Boolean,boolean)
LUA_JNI_NEW_WRAPPER(Byte,byte)
LUA_JNI_NEW_WRAPPER(Char,char)
LUA_JNI_NEW_WRAPPER(Short,short)
LUA_JNI_NEW_WRAPPER(Int,int)
LUA_JNI_NEW_WRAPPER(Long,long)
LUA_JNI_NEW_WRAPPER(Float,float)
LUA_JNI_NEW_WRAPPER(Double,double)
#undef LUA_JNI_NEW_WRAPPER


jboolean luaJniBooleanValue(JNIEnv*env, jobject obj){
    return (*env)->CallBooleanMethod(env,obj,context->booleanValue);
}

jbyte luaJniByteValue(JNIEnv*env, jobject obj){
    return (*env)->CallByteMethod(env,obj,context->byteValue);
}

jchar luaJniCharValue(JNIEnv*env, jobject obj){
    return (*env)->CallCharMethod(env,obj,context->charValue);
}

jshort luaJniShortValue(JNIEnv*env, jobject obj){
    return (*env)->CallShortMethod(env,obj,context->shortValue);
}

jint luaJniIntValue(JNIEnv*env, jobject obj){
    return (*env)->CallIntMethod(env,obj,context->intValue);
}

jlong luaJniLongValue(JNIEnv*env, jobject obj){
    return (*env)->CallLongMethod(env,obj,context->longValue);
}

jfloat luaJniFloatValue(JNIEnv*env, jobject obj){
    return (*env)->CallFloatMethod(env,obj,context->floatValue);
}

jdouble luaJniDoubleValue(JNIEnv*env, jobject obj){
    return (*env)->CallDoubleMethod(env,obj,context->doubleValue);
}
