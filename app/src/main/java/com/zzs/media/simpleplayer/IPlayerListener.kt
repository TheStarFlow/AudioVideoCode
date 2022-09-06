package com.zzs.media.simpleplayer

/**
@author  zzs
@Date 2022/9/6
@describe
 */
interface IPlayerListener {
    /**
     * 获取媒体文件信息,回调不在UI线程
     * */
    fun onFetchMediaInfo(durationMs: Long, width: Int, height: Int)

}