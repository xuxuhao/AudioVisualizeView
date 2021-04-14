package com.example.audiodemo

import android.content.Context
import android.media.*
import android.os.Build
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Created by xuhao on 2021/3/16.
 */
class AudioTrackPlayer {

    companion object {
        const val TAG = "AudioTrackPlayer"
    }

    // 音频数据
    private var mData: ByteArray? = null
    // AudioTrack对象
    private var mAudioTrack : AudioTrack? = null
    //当前播放位置
    private var mPlayOffset = 0
    //当前写入AudioTrack数据size
    private var mPlaySize = 0
    //缓冲buffer大小
    private var mBufferSizeInByte: Int = 0
    //pcm文件流
    private var mFileInputStream: InputStream? = null
    // 播放线程
    val mPlayAudioThread by lazy {
        object : Thread() {
            override fun run() {
                mAudioTrack?.play()
                val buffer = ByteArray(mBufferSizeInByte)
                try {
                    while ((mFileInputStream?.read(buffer) ?: 0) > 0) {
                        mAudioTrack?.write(buffer, 0, mBufferSizeInByte)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

//                while (true) {
//                    try {
//                        mPlayOffset = mData?.let { mAudioTrack?.write(it, mPlayOffset, mPlaySize) }
//                            ?: 0
//                        mPlayOffset += mPlaySize
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                        break
//                    }
//
//                    if (mPlayOffset >= (mData?.size ?: 0)) {
//                        doPlayComplete()
//                        break
//                    }
//                }
                mAudioTrack?.stop()
                mAudioTrack?.release()
            }
        }
    }

    private fun doPlayComplete() {
        TODO("Not yet implemented")
    }

    fun createAudioTrack() {
        if (mAudioTrack != null) {
            return
        }
        mBufferSizeInByte = AudioRecord.getMinBufferSize(AudioParams.sampleRateInHz, AudioParams.channelConfig, AudioParams.audioFormat)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAudioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(AudioParams.sampleRateInHz)
                    .setEncoding(AudioParams.audioFormat)
                    .setChannelMask(AudioParams.channelConfig)
                    .build(),
                mBufferSizeInByte, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE  //在创建的时候不知道会话可以传入这个值
            )
        } else {
            mAudioTrack = AudioTrack(AudioManager.STREAM_MUSIC,
                AudioParams.sampleRateInHz,
                AudioParams.channelConfig,
                AudioParams.audioFormat,
                mBufferSizeInByte,
                AudioTrack.MODE_STREAM
            )
        }
    }

    fun setDataSource(data: ByteArray?) {
        data?.let {
            mData = data
        }
    }

    fun openPCMFile(context: Context, path: String) {
        try {
            mFileInputStream = context.assets.open(path)
//            mFileInputStream = FileInputStream(context, path)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }

    object AudioParams {
        //音频采样率 (MediaRecoder的采样率通常是8000Hz AAC的通常是44100Hz.设置采样率为44100目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
        const val sampleRateInHz = 44100
        //声道
        val channelConfig: Int = android.media.AudioFormat.CHANNEL_CONFIGURATION_DEFAULT
//        val channelConfig: Int = android.media.AudioFormat.CHANNEL_IN_MONO
        //数据格式  (指定采样的数据的格式和每次采样的大小)
        val audioFormat: Int = android.media.AudioFormat.ENCODING_PCM_16BIT
    }
}