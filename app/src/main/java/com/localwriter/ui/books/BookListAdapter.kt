package com.localwriter.ui.books

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.localwriter.data.db.entity.Book
import com.localwriter.databinding.ItemBookBinding

class BookListAdapter(
    private val onItemClick: (Book) -> Unit,
    private val onItemLongClick: (Book, View) -> Unit,
    private val onContinueRead: (Book) -> Unit = {}
) : ListAdapter<Book, BookListAdapter.BookViewHolder>(DiffCallback) {

    inner class BookViewHolder(
        private val binding: ItemBookBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(book: Book) {
            binding.tvBookTitle.text = book.title
            binding.tvBookAuthor.text = if (book.author.isNotBlank()) book.author else "未署名"
            binding.tvWordCount.text = formatWordCount(book.wordCount)

            val statusLabel = when (book.status) {
                "ONGOING"  -> "连载中"
                "FINISHED" -> "已完结"
                "PAUSED"   -> "暂停"
                else       -> book.status
            }
            binding.tvStatus.text = statusLabel
            binding.tvStatus.setBackgroundResource(
                when (book.status) {
                    "ONGOING"  -> com.localwriter.R.drawable.bg_tag_ongoing
                    "FINISHED" -> com.localwriter.R.drawable.bg_tag_finished
                    else       -> com.localwriter.R.drawable.bg_tag_paused
                }
            )

            // 根据书名哈希为封面设置独特颜色，书脊用更深的变体
            val coverColor = coverColorFor(book.title)
            binding.root.setCardBackgroundColor(coverColor)
            binding.viewSpine.setBackgroundColor(darken(coverColor, 0.75f))

            binding.root.setOnClickListener { onItemClick(book) }
            binding.root.setOnLongClickListener {
                onItemLongClick(book, it)
                true
            }
            binding.btnMore.setOnClickListener { onItemLongClick(book, it) }

            if (book.lastChapterId > 0) {
                binding.btnContinueRead.visibility = View.VISIBLE
                binding.btnContinueRead.setOnClickListener { onContinueRead(book) }
            } else {
                binding.btnContinueRead.visibility = View.GONE
            }
        }

        private fun formatWordCount(count: Int): String = when {
            count >= 10000 -> "%.1f万字".format(count / 10000.0)
            else -> "${count}字"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(old: Book, new: Book) = old.id == new.id
        override fun areContentsTheSame(old: Book, new: Book) = old == new

        /** 精选书皮配色：16 种深色，来自世界经典书籍封面设计 */
        private val COVER_PALETTE = intArrayOf(
            0xFF1E3A5F.toInt(), // 深海夜蓝
            0xFF1B5E20.toInt(), // 茂林深绿
            0xFF4A148C.toInt(), // 暗夜紫
            0xFF880E4F.toInt(), // 暗玫瑰
            0xFF004D40.toInt(), // 墨色青
            0xFFBF360C.toInt(), // 古铜橙
            0xFF263238.toInt(), // 铁灰蓝
            0xFF01579B.toInt(), // 皇家蓝
            0xFF3E2723.toInt(), // 深棕木
            0xFF006064.toInt(), // 孔雀青
            0xFF33691E.toInt(), // 橄榄绿
            0xFF4E342E.toInt(), // 咖啡棕
            0xFF1A237E.toInt(), // 靛蓝
            0xFF37474F.toInt(), // 深灰蓝
            0xFF6A1B9A.toInt(), // 葡萄紫
            0xFF0D47A1.toInt(), // 宝蓝
        )

        fun coverColorFor(title: String): Int {
            val hash = title.fold(0) { acc, c -> acc * 31 + c.code }
            return COVER_PALETTE[Math.abs(hash) % COVER_PALETTE.size]
        }

        /** 把颜色调暗（乘以 factor < 1.0） */
        fun darken(color: Int, factor: Float): Int {
            val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
            val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
            val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }
    }
}
