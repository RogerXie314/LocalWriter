package com.localwriter.ui.books

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
            binding.tvBookAuthor.text = if (book.author.isNotBlank()) "作者：${book.author}" else "未署名"
            binding.tvWordCount.text = formatWordCount(book.wordCount)
            binding.tvStatus.text = when (book.status) {
                "ONGOING"  -> "连载中"
                "FINISHED" -> "已完结"
                "PAUSED"   -> "暂停"
                else       -> book.status
            }
            binding.tvStatus.setBackgroundResource(
                when (book.status) {
                    "ONGOING"  -> com.localwriter.R.drawable.bg_tag_ongoing
                    "FINISHED" -> com.localwriter.R.drawable.bg_tag_finished
                    else       -> com.localwriter.R.drawable.bg_tag_paused
                }
            )

            binding.root.setOnClickListener { onItemClick(book) }
            binding.root.setOnLongClickListener {
                onItemLongClick(book, it)
                true
            }
            // 右上角三点按钮，与长按菜单一致，让操作可见
            binding.btnMore.setOnClickListener { onItemLongClick(book, it) }

            // 继续阅读按钮（有阅读记录时才显示）
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
    }
}
