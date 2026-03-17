package com.localwriter.ui.books

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.data.db.entity.Book
import com.localwriter.databinding.FragmentBookListBinding
import com.localwriter.databinding.DialogEditTitleBinding
import com.localwriter.ui.chapters.ChapterListFragment
import com.localwriter.ui.reader.ReaderActivity
import com.localwriter.ui.settings.SettingsActivity
import com.localwriter.utils.SessionManager
import com.localwriter.utils.io.BookExporter
import com.localwriter.utils.io.BookImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 左侧导航：书籍列表
 */
class BookListFragment : Fragment() {

    private var _binding: FragmentBookListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BookViewModel by activityViewModels {
        BookViewModel.Factory(
            (requireActivity().application as LocalWriterApp).bookRepository,
            requireActivity().applicationContext
        )
    }

    private lateinit var adapter: BookListAdapter

    /** 导出时暂存当前书籍 */
    private var pendingExportBook: Book? = null
    private var pendingExportFormat: BookExporter.ExportFormat? = null

    /** SAF 文件保存选择器（导出用） */
    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val book = pendingExportBook ?: return@registerForActivityResult
            val format = pendingExportFormat ?: return@registerForActivityResult
            doExport(book, format, uri)
        }
    }

    /** 文件选择器（导入用）—— GetContent 兼容更多国产/OEM 设备，星号斜杠 允许浏览所有文件 */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) processImport(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupImportButton()
        setupSortButton()
        setupSettingsLockButtons()
        observeData()
        observeEvents()
    }

    private fun setupSettingsLockButtons() {
        binding.ibSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.ibLock.setOnClickListener {
            SessionManager.lock(requireContext())
            requireActivity().finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = BookListAdapter(
            onItemClick = { book -> onBookSelected(book) },
            onItemLongClick = { book, anchorView -> showBookMenu(book, anchorView) },
            onContinueRead = { book -> openReaderForBook(book) }
        )
        binding.rvBooks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@BookListFragment.adapter
        }
    }

    /** 打开阅读器，从上次阅读章节继续 */
    private fun openReaderForBook(book: Book) {
        val chapterId = book.lastChapterId
        if (chapterId <= 0) {
            Toast.makeText(requireContext(), "暂无阅读记录，请先点入一个章节开始阅读", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(requireContext(), ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_CHAPTER_ID, chapterId)
            putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
        }
        startActivity(intent)
    }

    private fun setupFab() {
        binding.fabNewBook.setOnClickListener { showCreateBookDialog() }
    }

    private fun setupImportButton() {
        binding.ibImportBook.setOnClickListener {
            // 使用 */* 让系统文件管理器显示所有文件，兼容国产/OEM 设备
            importLauncher.launch("*/*")
        }
    }

    private fun setupSortButton() {
        binding.ibSortBooks.setOnClickListener { viewModel.toggleSort() }
        // 根据排序模式更新图标颜色（激活状态高亮）
        viewModel.isSortByRecent.observe(viewLifecycleOwner) { isRecent ->
            val tint = if (isRecent)
                requireContext().getColor(android.R.color.holo_orange_light)
            else
                requireContext().getColor(android.R.color.white)
            binding.ibSortBooks.setColorFilter(tint)
        }
    }

    private fun observeData() {
        viewModel.books.observe(viewLifecycleOwner) { books ->
            adapter.submitList(books)
            binding.layoutEmpty.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun observeEvents() {
        viewModel.uiEvent.observe(viewLifecycleOwner) { event ->
            when (event) {
                is BookViewModel.UiEvent.ShowError ->
                    Toast.makeText(context, event.msg, Toast.LENGTH_SHORT).show()
                is BookViewModel.UiEvent.BookCreated ->
                    Toast.makeText(context, "书籍已创建", Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }
    }

    private fun onBookSelected(book: Book) {
        viewModel.selectBook(book)
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_panel_container, ChapterListFragment.newInstance(book.id))
            .addToBackStack(null)
            .commit()
    }

    private fun showBookMenu(book: Book, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_book_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename      -> showRenameDialog(book)
                R.id.action_edit_info   -> showEditBookInfoDialog(book)
                R.id.action_delete      -> showDeleteConfirm(book)
                R.id.action_export      -> showExportDialog(book)
                R.id.action_change_status -> showStatusDialog(book)
            }
            true
        }
        popup.show()
    }

    // ─────────────────── 新建书籍 ───────────────────

    private fun showCreateBookDialog() {
        val dialogBinding = DialogEditTitleBinding.inflate(layoutInflater)
        dialogBinding.etTitle.hint = "请输入书名"
        AlertDialog.Builder(requireContext())
            .setTitle("新建书籍")
            .setView(dialogBinding.root)
            .setPositiveButton("创建") { _, _ ->
                val title = dialogBinding.etTitle.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(context, "书名不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.createBook(title)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ─────────────────── 重命名 ───────────────────

    private fun showRenameDialog(book: Book) {
        val dialogBinding = DialogEditTitleBinding.inflate(layoutInflater)
        dialogBinding.etTitle.setText(book.title)
        AlertDialog.Builder(requireContext())
            .setTitle("重命名书籍")
            .setView(dialogBinding.root)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = dialogBinding.etTitle.text.toString().trim()
                if (newTitle.isNotEmpty()) viewModel.updateBook(book.copy(title = newTitle))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ─────────────────── 书籍信息编辑 ───────────────────

    private fun showEditBookInfoDialog(book: Book) {
        val ctx = requireContext()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8 * 3, dp8, dp8 * 3, dp8)
        }

        val etAuthor = EditText(ctx).apply {
            hint = "作者（选填）"
            setText(book.author)
            setSingleLine()
        }
        val etDesc = EditText(ctx).apply {
            hint = "简介（选填）"
            setText(book.description)
            minLines = 3
            maxLines = 5
        }
        container.addView(etAuthor)
        container.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp8
            )
        })
        container.addView(etDesc)

        AlertDialog.Builder(ctx)
            .setTitle("编辑书籍信息")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val author = etAuthor.text.toString().trim()
                val desc   = etDesc.text.toString().trim()
                viewModel.updateBook(book.copy(author = author, description = desc))
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ─────────────────── 删除 ───────────────────

    private fun showDeleteConfirm(book: Book) {
        val ctx = requireContext()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        val etConfirm = EditText(ctx).apply {
            hint = "请输入书名以确认"
            setSingleLine()
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8 * 3, dp8, dp8 * 3, dp8)
            addView(etConfirm)
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("删除书籍")
            .setMessage("此操作将删除《${book.title}》的全部章节且不可恢复！\n请输入书名「${book.title}」以确认：")
            .setView(container)
            .setPositiveButton("删除", null)   // 先设置 null，后面手动处理防止自动关闭
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val input = etConfirm.text.toString().trim()
                if (input == book.title) {
                    viewModel.deleteBook(book)
                    dialog.dismiss()
                } else {
                    etConfirm.error = "书名不匹配"
                }
            }
        }
        dialog.show()
    }

    // ─────────────────── 导出 ───────────────────

    private fun showExportDialog(book: Book) {
        val formats = arrayOf("TXT 纯文本", "EPUB 电子书", "PDF 文档", "DOCX Word")
        val formatValues = arrayOf(
            BookExporter.ExportFormat.TXT,
            BookExporter.ExportFormat.EPUB,
            BookExporter.ExportFormat.PDF,
            BookExporter.ExportFormat.DOCX
        )
        val extensions = arrayOf("txt", "epub", "pdf", "docx")
        val mimeTypes  = arrayOf("text/plain", "application/epub+zip",
                                 "application/pdf",
                                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

        var selectedIndex = 0
        AlertDialog.Builder(requireContext())
            .setTitle("导出《${book.title}》")
            .setSingleChoiceItems(formats, 0) { _, which -> selectedIndex = which }
            .setPositiveButton("导出") { _, _ ->
                val format = formatValues[selectedIndex]
                val ext    = extensions[selectedIndex]
                val mime   = mimeTypes[selectedIndex]
                val safeName = "${book.title}.${ext}"
                pendingExportBook   = book
                pendingExportFormat = format
                // 打开系统文件选择器，让用户选择保存位置
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    putExtra(Intent.EXTRA_TITLE, safeName)
                }
                saveLauncher.launch(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doExport(book: Book, format: BookExporter.ExportFormat, uri: android.net.Uri) {
        val app      = requireActivity().application as LocalWriterApp
        val bookRepo = app.bookRepository
        val db       = app.database   // 在主线程提前缓存，避免在 withContext(IO) 内调用 requireActivity()
        val ctx      = requireContext().applicationContext

        lifecycleScope.launch {
            try {
                // 获取该书所有章节（非删除）
                val chapters = withContext(Dispatchers.IO) {
                    val books = bookRepo.getAllBooks(
                        com.localwriter.utils.SessionManager.getUserId(ctx)
                    )
                    val targetBook = books.firstOrNull { it.id == book.id } ?: book
                    // 通过 DAO 获取所有章节内容
                    val volumes = db.volumeDao().getAllByBook(book.id)
                    val allChapters = mutableListOf<com.localwriter.data.db.entity.Chapter>()
                    volumes.forEach { vol ->
                        allChapters.addAll(db.chapterDao().getAllByVolume(vol.id))
                    }
                    BookExporter.ExportBook(targetBook, allChapters)
                }
                withContext(Dispatchers.IO) {
                    BookExporter.export(ctx, chapters, format, uri)
                }
                Toast.makeText(ctx, "导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─────────────────── 状态 ───────────────────

    private fun showStatusDialog(book: Book) {
        val statuses     = arrayOf("连载中", "已完结", "暂停")
        val statusValues = arrayOf("ONGOING", "FINISHED", "PAUSED")
        val currentIndex = statusValues.indexOf(book.status).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle("修改书籍状态")
            .setSingleChoiceItems(statuses, currentIndex) { dialog, which ->
                viewModel.updateBook(book.copy(status = statusValues[which]))
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────── 导入书籍 ───────────────────

    /**
     * 取得 URI 对应的文件名
     */
    private fun getFilename(uri: Uri): String {
        var name = "unknown"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    /**
     * 导入流程：解析 → 预览 → 确认保存
     */
    private fun processImport(uri: Uri) {
        val ctx = requireContext()
        val filename = getFilename(uri)

        // 1. 进度对话框（用 AlertDialog + ProgressBar，不依赖已废弃的 ProgressDialog）
        val progressBar = ProgressBar(ctx).apply {
            isIndeterminate = true
            setPadding(64, 40, 64, 20)
        }
        val progressDialog = AlertDialog.Builder(ctx)
            .setTitle("正在解析")
            .setMessage("《${filename.substringBeforeLast('.')}》")
            .setView(progressBar)
            .setCancelable(false)
            .create()
            .also { it.show() }

        lifecycleScope.launch {
            try {
                // 2. IO 线程解析
                val result = withContext(Dispatchers.IO) {
                    BookImporter.import(ctx, uri, filename)
                }
                progressDialog.dismiss()

                // 3. 预览对话框
                val chapterCount = result.chapters.size
                val totalWords   = result.chapters.sumOf { it.content.length }
                val preview = buildString {
                    append("书名：${result.title}\n")
                    if (result.author.isNotEmpty()) append("作者：${result.author}\n")
                    append("章节：$chapterCount 章\n")
                    append("总字数：约 ${totalWords / 10000}.${(totalWords % 10000) / 1000} 万字")
                }

                AlertDialog.Builder(ctx)
                    .setTitle("导入预览")
                    .setMessage(preview)
                    .setPositiveButton("确认导入") { _, _ ->
                        saveImportedBook(result)
                    }
                    .setNegativeButton("取消", null)
                    .show()

            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(ctx, "解析失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 将导入结果存入数据库
     */
    private fun saveImportedBook(result: com.localwriter.utils.io.ImportResult) {
        val ctx    = requireContext()
        val userId = SessionManager.getUserId(ctx)
        // 在主线程提前缓存，避免协程启动后 Fragment 已离开导致 requireActivity() 抛异常
        val app    = requireActivity().application as LocalWriterApp

        lifecycleScope.launch {
            try {
                val bookRepo = app.bookRepository
                val bookId   = withContext(Dispatchers.IO) {
                    bookRepo.importBook(userId, result)
                }
                val count = result.chapters.size
                Toast.makeText(ctx, "《${result.title}》导入成功，共 $count 章", Toast.LENGTH_LONG).show()

                // H3: 询问是否立即阅读第一章
                val firstChapterId = withContext(Dispatchers.IO) {
                    val db = app.database
                    val vol = db.volumeDao().getAllByBook(bookId).minByOrNull { it.sortOrder }
                    if (vol != null) {
                        db.chapterDao().getAllByVolume(vol.id)
                            .filter { it.status != "DELETED" }
                            .minByOrNull { it.sortOrder }?.id
                    } else null
                }

                if (firstChapterId != null && isAdded) {
                    AlertDialog.Builder(ctx)
                        .setTitle("导入成功")
                        .setMessage("《${result.title}》已导入完成，是否立即阅读第一章？")
                        .setPositiveButton("立即阅读") { _, _ ->
                            val intent = Intent(ctx, ReaderActivity::class.java).apply {
                                putExtra(ReaderActivity.EXTRA_CHAPTER_ID, firstChapterId)
                                putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("稍后阅读", null)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
