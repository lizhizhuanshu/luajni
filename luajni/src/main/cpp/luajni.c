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



static HashMap *g_map = NULL;




HashMap* createHashMap();
unsigned int hashMapHash(const char*key);
void hashMapPut(HashMap*map, const char*key, LuaJniInjectMethod method, void*userData);
Value* hashMapGet(HashMap*map, const char*key);
void* hashMapRemove(HashMap*map, const char*key);
void hashMapClear(HashMap*map);
void hashMapDestroy(HashMap*map);
HashMap *ensureHashMap();


int luaJniEqualJavaArray(JavaArray* a, const char*className, int level, enum ARRAY_ELEMENT_TYPE elementType){
    return a != NULL &&
    a->level == level &&
    a->elementType == elementType &&
    ((a->name == NULL && className == NULL) || (a->name != NULL && className != NULL && strcmp(a->name,className) == 0));
}


int64_t luaJniCacheJavaObject(JNIEnv*env, jobject obj){
    jobject globalRef = (*env)->NewGlobalRef(env,obj);
    return (int64_t)globalRef;
}

void luaJniReleaseJavaObject(JNIEnv*env, int64_t id){
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
            newArray->id = luaJniCacheJavaObject(env, nArray);
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
                    int64_t id = luaJniCacheJavaObject(env, value);
                    JavaObject *object = (JavaObject *) lua_newuserdata(L, sizeof(JavaObject));
                    object->id = id;
                    (*env)->DeleteLocalRef(env,value);
                    int r = luaL_getmetatable(L, array->name);
                    if (r) {
                        lua_setmetatable(L, -2);
                    } else {
                        luaJniReleaseJavaObject(env, id);
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


int luaJniPushIntField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jint value = (*env)->GetIntField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    lua_pushinteger(L,value);
    return 1;
}
int luaJniPushLongField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jlong value = (*env)->GetLongField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    lua_pushinteger(L,value);
    return 1;
}
int luaJniPushShortField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jshort value = (*env)->GetShortField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    lua_pushinteger(L,value);
    return 1;
}
int luaJniPushByteField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jbyte value = (*env)->GetByteField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    lua_pushinteger(L,value);
    return 1;
}
int luaJniPushBooleanField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jboolean value = (*env)->GetBooleanField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    lua_pushboolean(L,value);
    return 1;
}
int luaJniPushFloatField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jfloat value = (*env)->GetFloatField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    lua_pushnumber(L,value);
    return 1;
}
int luaJniPushDoubleField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jdouble value = (*env)->GetDoubleField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    lua_pushnumber(L,value);
    return 1;
}
int luaJniPushCharField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jchar value = (*env)->GetCharField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    lua_pushinteger(L,value);
    return 1;
}
int luaJniPushStringField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field){
    jstring value = (*env)->GetObjectField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    if(value != NULL){
        const char *str = (*env)->GetStringUTFChars(env,value,0);
        lua_pushstring(L,str);
        (*env)->ReleaseStringUTFChars(env,value,str);
        (*env)->DeleteLocalRef(env,value);
    }else{
        lua_pushnil(L);
    }
    return 1;
}
int luaJniPushObjectField(lua_State*L,JNIEnv*env,jobject obj,jfieldID field,const char*className){
    jobject value = (*env)->GetObjectField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    if(value != NULL){
        int64_t id = luaJniCacheJavaObject(env, value);
        (*env)->DeleteLocalRef(env,value);
        JavaObject *object = (JavaObject *) lua_newuserdata(L,sizeof(JavaObject));
        object->id = id;
        int r = luaL_getmetatable(L,className);
        if(r){
            lua_setmetatable(L,-2);
        }else{
            lua_pushfstring(L,"can not find metatable for %s",className);
            return 0;
        }
    }else{
        lua_pushnil(L);
    }
    return 1;
}
int luaJniPushArrayField(lua_State*L,JNIEnv*env,jobject obj,jfieldID field,const char*className,int level,
                   enum ARRAY_ELEMENT_TYPE elementType){
    jobjectArray value = (*env)->GetObjectField(env,obj,field);
    if(luaJniCatchJavaException(L, env)) return 0;
    if(value != NULL){
        JavaArray *array = (JavaArray *) lua_newuserdata(L,sizeof(JavaArray));
        array->id = (int64_t)value;
        array->level = level;
        array->name = className;
        array->elementType = elementType;
        luaL_getmetatable(L,JAVA_ARRAY_META_NAME);
        lua_setmetatable(L,-2);
    }else{
        lua_pushnil(L);
    }
    return 1;
}


HashMap *createHashMap() {
    HashMap *map = (HashMap *) malloc(sizeof(HashMap));
    memset(map, 0, sizeof(HashMap));
    return map;
}

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

void hashMapDestroy(HashMap *map) {
    hashMapClear(map);
    free(map);
}

HashMap *ensureHashMap() {
    if (g_map == NULL) {
        g_map = createHashMap();
    }
    return g_map;
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