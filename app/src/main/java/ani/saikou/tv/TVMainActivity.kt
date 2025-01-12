package ani.saikou.tv

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.TvContractCompat
import ani.saikou.*
import ani.saikou.anilist.Anilist
import ani.saikou.anilist.AnilistHomeViewModel
import ani.saikou.media.MediaDetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

class TVMainActivity : FragmentActivity() {
    private val scope = lifecycleScope
    private var load = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_activity_main)
        loadMedia = intent?.getIntExtra("media", -1)

        if (!isOnline(this)) {
            //toastString("No Internet Connection")
            startActivity(Intent(this, NoInternet::class.java))
        } else {
            val  model : AnilistHomeViewModel by viewModels()
            model.genres.observe(this) {
                if (it!=null) {
                    if(it) {
                        if (loadMedia != null || loadMedia != -1) {
                            scope.launch {
                                val media = withContext(Dispatchers.IO) {
                                    Anilist.query.getMedia(
                                        loadMedia!!,
                                        loadIsMAL
                                    )
                                }
                                if (media != null) {
                                    startActivity(
                                        Intent(
                                            this@TVMainActivity,
                                            TVAnimeDetailActivity::class.java
                                        ).putExtra("media", media as Serializable)
                                    )
                                } else {
                                    //toastString("Seems like that wasn't found on Anilist.")
                                }
                            }
                        }
                    }
                    else {
                        //toastString("Error Loading Tags & Genres.")
                    }
                }
            }

            //Load Data
            if (!load) {
                Anilist.getSavedToken(this)
                scope.launch(Dispatchers.IO) {
                    model.genres.postValue(Anilist.query.getGenresAndTags(this@TVMainActivity))
                    //AppUpdater.check(this@MainActivity)
                }
                load = true
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, TVAnimeFragment())
                .commitNow()
        }
        createHomeTVChannel()
    }

    fun createHomeTVChannel() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        if (!sharedPref.contains(defaultChannelIDKey)) {
            val builder = Channel.Builder()

            builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName("Trending animes")
                .setAppLinkIntentUri(Uri.parse("saikoutv://home"))

            var channelUri = contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI, builder.build().toContentValues()
            )

            channelUri?.let {
                var channelID = ContentUris.parseId(channelUri)

                with(sharedPref.edit()) {
                    putLong(defaultChannelIDKey, channelID)
                    apply()
                }

                ChannelLogoUtils.storeChannelLogo(this, channelID, getDrawable(R.drawable.saikouflush)!!.toBitmap())
                TvContractCompat.requestChannelBrowsable(this, channelID)
            }
        }
    }

    companion object {
        final val defaultChannelIDKey = "default_tv_channel_id"
        final val defaultChannelProgramsKey = "default_tv_programs_list_id"

    }
}