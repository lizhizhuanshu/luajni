
cmake_minimum_required(VERSION 3.22.1)

project(engine)
set(CMAKE_CXX_STANDARD 17)


#compile lua
add_definitions(-DLUA_DL_DLOPEN -DLUA_COMPAT_5_3 -DLUA_USE_LINUX)
set(MY_LUA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/3rd/lua)
include_directories(${MY_LUA_DIR})
aux_source_directory(${MY_LUA_DIR}  LUA_DIR)
list(REMOVE_ITEM LUA_DIR "${MY_LUA_DIR}/lua.c"
        "${MY_LUA_DIR}/luac.c" "${MY_LUA_DIR}/onelua.c")
add_library(lua_static STATIC ${LUA_DIR})


if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(BUILD_TYPE_DIR "debug")
elseif(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(BUILD_TYPE_DIR "release")
else()
    set(BUILD_TYPE_DIR "debug")
endif()


include_directories(${CMAKE_CURRENT_SOURCE_DIR})
add_library(luajni STATIC luajni.c)

set(extensionDir "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/source/kapt/${BUILD_TYPE_DIR}/cpp")

set(LUA_JNI_LIB_NAMES "luajni;lua_static" CACHE STRING "Lua JNI lib names" FORCE)
set(LUA_JNI_SHARED OFF CACHE BOOL "Build shared library" FORCE)
set(LUA_JNI_AUTO_REGISTER ON CACHE BOOL "Auto register" FORCE)

add_subdirectory(${extensionDir}
        "${CMAKE_CURRENT_BINARY_DIR}/generated_cpp_build")

add_library(engine SHARED "${extensionDir}/lua_jni_extension.h" interpreter.c )
target_include_directories(engine PRIVATE ${extensionDir})

target_link_libraries(engine
        log
        lua_static
        luajni
        lua_jni_extension
)