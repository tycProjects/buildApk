package com.editorpro
class VideoModule { fun canDecode(codec:String)=codec.lowercase() in setOf("h264","hevc","vp9") }
