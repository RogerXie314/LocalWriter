package com.localwriter.ui.chapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.localwriter.data.db.dao.ChapterPreview
import com.localwriter.data.db.entity.Volume
import com.localwriter.databinding.ItemChapterBinding
import com.localwriter.databinding.ItemVolumeSectionBinding

/**
 * 支持分卷的章节列表 Adapter
 * 数据结构：卷 Header + 章节 Item（扁平化处理）
 */
class ChapterSectionAdapter(
    private val onChapterClick: (ChapterPreview) -> Unit,
    private val onChapterLongClick: (ChapterPreview, View) -> Unit,
    private val onAddChapter: (Volume) -> Unit,
    private val onVolumeMenu: (Volume, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_VOLUME_HEADER = 0
        const val TYPE_CHAPTER = 1
    }

    sealed class ListItem {
        data class VolumeHeader(val volume: Volume) : ListItem()
        data class ChapterItem(val chapter: ChapterPreview, val volume: Volume) : ListItem()
    }

    private val items = mutableListOf<ListItem>()

    fun submitData(volumes: List<Volume>, chapters: List<ChapterPreview>) {
        items.clear()
        volumes.forEach { volume ->
            items.add(ListItem.VolumeHeader(volume))
            chapters.filter { it.volumeId == volume.id }
                .sortedBy { it.sortOrder }
                .forEach { chapter ->
                    items.add(ListItem.ChapterItem(chapter, volume))
                }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.VolumeHeader -> TYPE_VOLUME_HEADER
        is ListItem.ChapterItem -> TYPE_CHAPTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_VOLUME_HEADER -> {
                val binding = ItemVolumeSectionBinding
                    .inflate(LayoutInflater.from(parent.context), parent, false)
                VolumeViewHolder(binding)
            }
            else -> {
                val binding = ItemChapterBinding
                    .inflate(LayoutInflater.from(parent.context), parent, false)
                ChapterViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.VolumeHeader -> (holder as VolumeViewHolder).bind(item.volume)
            is ListItem.ChapterItem  -> (holder as ChapterViewHolder).bind(item.chapter, item.volume)
        }
    }

    override fun getItemCount() = items.size

    inner class VolumeViewHolder(private val binding: ItemVolumeSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(volume: Volume) {
            binding.tvVolumeTitle.text = volume.title
            binding.btnAddChapter.setOnClickListener { onAddChapter(volume) }
            binding.btnVolumeMenu.setOnClickListener { onVolumeMenu(volume, it) }
        }
    }

    inner class ChapterViewHolder(private val binding: ItemChapterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @Suppress("UNUSED_PARAMETER")
        fun bind(chapter: ChapterPreview, volume: Volume) {
            binding.tvChapterTitle.text = chapter.title
            binding.tvWordCount.text = formatCount(chapter.wordCount)
            binding.tvStatus.visibility = if (chapter.status == "DRAFT") View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onChapterClick(chapter) }
            binding.root.setOnLongClickListener {
                onChapterLongClick(chapter, it)
                true
            }
            // 右侧三点按钮，与长按菜单一致，让操作可见
            binding.btnChapterMore.setOnClickListener { onChapterLongClick(chapter, it) }
        }

        private fun formatCount(count: Int): String = when {
            count >= 10000 -> "%.1f万".format(count / 10000.0)
            else -> "${count}字"
        }
    }
}
