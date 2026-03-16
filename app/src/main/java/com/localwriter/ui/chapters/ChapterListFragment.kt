package com.localwriter.ui.chapters

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.data.db.dao.ChapterPreview
import com.localwriter.data.db.entity.Chapter
import com.localwriter.data.db.entity.Volume
import com.localwriter.databinding.FragmentChapterListBinding
import com.localwriter.databinding.DialogEditTitleBinding
import com.localwriter.ui.editor.EditorActivity
import com.localwriter.ui.reader.ReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 左侧导航：章节列表（按卷分组）
 *
 * 章节管理逻辑：
 * - 显示书名标题栏 + 返回按钮（返回书籍列表）
 * - 按"卷"分组展示章节（可折叠）
 * - 顶部有"新建卷"按钮，每卷右侧有"新建章节"按钮
 * - 章节项长按：重命名/删除（移入回收站）/移动到其他卷
 * - 底部有"回收站"入口（查看回收站章节）
 * - 支持拖拽排序（通过 ItemTouchHelper）
 * - 搜索按钮：全文搜索章节标题/内容
 */
class ChapterListFragment : Fragment() {

    private var _binding: FragmentChapterListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChapterViewModel by activityViewModels {
        ChapterViewModel.Factory(
            (requireActivity().application as LocalWriterApp).chapterRepository,
            (requireActivity().application as LocalWriterApp).bookRepository
        )
    }

    private var bookId: Long = 0
    private lateinit var adapter: ChapterSectionAdapter

    companion object {
        private const val ARG_BOOK_ID = "book_id"
        fun newInstance(bookId: Long) = ChapterListFragment().apply {
            arguments = Bundle().apply { putLong(ARG_BOOK_ID, bookId) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId = arguments?.getLong(ARG_BOOK_ID) ?: 0
        viewModel.setBookId(bookId)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChapterListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeData()
        observeEvents()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> showSearchDialog()
                R.id.action_trash -> showTrashDialog()
                R.id.action_add_volume -> showAddVolumeDialog()
            }
            true
        }
    }

    private fun setupRecyclerView() {
        adapter = ChapterSectionAdapter(
            onChapterClick = { chapter -> viewModel.openChapter(chapter.id, bookId) },
            onChapterLongClick = { chapter, anchor -> showChapterMenu(chapter, anchor) },
            onAddChapter = { volume -> showAddChapterDialog(volume) },
            onVolumeMenu = { volume, anchor -> showVolumeMenu(volume, anchor) }
        )
        binding.rvChapters.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ChapterListFragment.adapter
        }
    }

    private fun observeData() {
        // 独立监听卷列表和章节列表，避免嵌套 observe 导致 Observer 累积内存泄漏
        var latestVolumes: List<com.localwriter.data.db.entity.Volume> = emptyList()
        var latestChapters: List<com.localwriter.data.db.dao.ChapterPreview> = emptyList()

        viewModel.volumes.observe(viewLifecycleOwner) { volumes ->
            latestVolumes = volumes ?: emptyList()
            adapter.submitData(latestVolumes, latestChapters)
            binding.tvEmpty.visibility = if (latestVolumes.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.allChapters.observe(viewLifecycleOwner) { chapters ->
            latestChapters = chapters ?: emptyList()
            adapter.submitData(latestVolumes, latestChapters)
        }
    }

    private fun observeEvents() {
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is ChapterViewModel.UiEvent.NavigateToReader -> {
                    val intent = Intent(requireActivity(), ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_CHAPTER_ID, event.chapterId)
                        putExtra(ReaderActivity.EXTRA_BOOK_ID, event.bookId)
                    }
                    startActivity(intent)
                }
                is ChapterViewModel.UiEvent.NavigateToEditor -> {
                    val intent = Intent(requireActivity(), EditorActivity::class.java).apply {
                        putExtra(EditorActivity.EXTRA_CHAPTER_ID, event.chapterId)
                        putExtra(EditorActivity.EXTRA_BOOK_ID, event.bookId)
                    }
                    startActivity(intent)
                }
                is ChapterViewModel.UiEvent.ShowError ->
                    Toast.makeText(context, event.msg, Toast.LENGTH_SHORT).show()
                is ChapterViewModel.UiEvent.ChapterCreated ->
                    Toast.makeText(context, "章节已创建", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChapterMenu(chapter: ChapterPreview, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_chapter_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_read   -> viewModel.openChapter(chapter.id, bookId)
                R.id.action_edit   -> viewModel.editChapter(chapter.id, bookId)
                R.id.action_rename -> showRenameChapterDialog(chapter)
                R.id.action_delete -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle("删除章节")
                        .setMessage("将《${chapter.title}》移入回收站？\n（30天内可恢复）")
                        .setPositiveButton("删除") { _, _ ->
                            viewModel.deleteChapter(chapter.id, bookId)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            true
        }
        popup.show()
    }

    private fun showVolumeMenu(volume: Volume, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_volume_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> {
                    val dialogBinding = DialogEditTitleBinding.inflate(layoutInflater)
                    dialogBinding.etTitle.setText(volume.title)
                    AlertDialog.Builder(requireContext())
                        .setTitle("重命名卷")
                        .setView(dialogBinding.root)
                        .setPositiveButton("确定") { _, _ ->
                            val newTitle = dialogBinding.etTitle.text.toString().trim()
                            if (newTitle.isNotEmpty()) {
                                viewModel.renameVolume(volume.copy(title = newTitle))
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                R.id.action_delete -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle("删除卷")
                        .setMessage("删除\"${volume.title}\"及其所有章节？\n此操作不可恢复！")
                        .setPositiveButton("删除") { _, _ ->
                            viewModel.deleteVolume(volume)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            true
        }
        popup.show()
    }

    private fun showAddChapterDialog(volume: Volume) {
        val dialogBinding = DialogEditTitleBinding.inflate(layoutInflater)
        dialogBinding.etTitle.hint = "请输入章节标题，如：第一章 初入江湖"
        AlertDialog.Builder(requireContext())
            .setTitle("新建章节")
            .setView(dialogBinding.root)
            .setPositiveButton("创建") { _, _ ->
                val title = dialogBinding.etTitle.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(context, "章节标题不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.createChapter(bookId, volume.id, title)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddVolumeDialog() {
        val dialogBinding = DialogEditTitleBinding.inflate(layoutInflater)
        dialogBinding.etTitle.hint = "如：第二卷 天下"
        AlertDialog.Builder(requireContext())
            .setTitle("新建卷")
            .setView(dialogBinding.root)
            .setPositiveButton("创建") { _, _ ->
                val title = dialogBinding.etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    viewModel.createVolume(bookId, title)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameChapterDialog(chapter: ChapterPreview) {
        val dialogBinding = DialogEditTitleBinding.inflate(layoutInflater)
        dialogBinding.etTitle.setText(chapter.title)
        AlertDialog.Builder(requireContext())
            .setTitle("重命名章节")
            .setView(dialogBinding.root)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = dialogBinding.etTitle.text.toString().trim()
                if (newTitle.isNotEmpty()) viewModel.updateTitle(chapter.id, newTitle)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSearchDialog() {
        val ctx = requireContext()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val etQuery = android.widget.EditText(ctx).apply {
            hint = "搜索章节标题或正文"
            setSingleLine()
            setPadding(dp8 * 3, dp8, dp8 * 3, dp8)
        }

        AlertDialog.Builder(ctx)
            .setTitle("全文搜索")
            .setView(etQuery)
            .setPositiveButton("搜索") { _, _ ->
                val query = etQuery.text.toString().trim()
                if (query.isEmpty()) return@setPositiveButton
                showSearchResults(query)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSearchResults(query: String) {
        val ctx = requireContext()
        // 在主线程（挂起前）缓存 db 引用，避免在 withContext(IO) 内调用 requireActivity() 
        // 引发 "Fragment not attached" 的 IllegalStateException
        val db = (requireActivity().application as LocalWriterApp).database
        lifecycleScope.launch {
            val results: List<ChapterPreview> = withContext(Dispatchers.IO) {
                db.chapterDao().search(bookId, query)
            }
            if (results.isEmpty()) {
                Toast.makeText(ctx, "未找到「$query」相关章节", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val titles = results.map { it.title }.toTypedArray()
            AlertDialog.Builder(ctx)
                .setTitle("搜索结果（${results.size}条）")
                .setItems(titles) { _, which ->
                    val chapter = results[which]
                    // M7: 为每个结果提供阅读/编辑双选项
                    AlertDialog.Builder(ctx)
                        .setTitle("《${chapter.title}》")
                        .setPositiveButton("阅读") { _, _ ->
                            viewModel.openChapter(chapter.id, bookId)
                        }
                        .setNeutralButton("编辑") { _, _ ->
                            viewModel.editChapter(chapter.id, bookId)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private fun showTrashDialog() {
        val ctx = requireContext()
        val chapterRepo = (requireActivity().application as LocalWriterApp).chapterRepository
        // 使用 suspend 一次性查询，避免每次调用 observeDeletedChapters() 都创建新 LiveData 实例
        // 导致 removeObservers 作用于错误实例、观察者无限积累的崩溃问题
        lifecycleScope.launch {
            val deletedList = withContext(Dispatchers.IO) {
                chapterRepo.getDeletedChapters(bookId)
            }
            if (!isAdded) return@launch  // Fragment 已离开，放弃操作

            if (deletedList.isEmpty()) {
                Toast.makeText(ctx, "回收站为空", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val titles = deletedList.map { "《${it.title}》" }.toTypedArray()
            AlertDialog.Builder(ctx)
                .setTitle("回收站（${deletedList.size}章，30天后自动清除）")
                .setItems(titles) { _, which ->
                    val chapter = deletedList[which]
                    AlertDialog.Builder(ctx)
                        .setTitle("操作章节")
                        .setMessage("《${chapter.title}》")
                        .setPositiveButton("恢复") { _, _ ->
                            viewModel.restoreChapter(chapter.id, bookId)
                            Toast.makeText(ctx, "已恢复", Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton("永久删除") { _, _ ->
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    chapterRepo.permanentDeleteChapter(chapter, bookId)
                                }
                                Toast.makeText(ctx, "已永久删除", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                // M5: 批量操作按钮
                .setPositiveButton("全部恢复") { _, _ ->
                    viewModel.restoreAll(bookId)
                    Toast.makeText(ctx, "已全部恢复", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("清空回收站") { _, _ ->
                    AlertDialog.Builder(ctx)
                        .setTitle("清空回收站")
                        .setMessage("确定永久删除全部 ${deletedList.size} 个章节？此操作不可恢复！")
                        .setPositiveButton("确认清空") { _, _ ->
                            viewModel.clearTrash(bookId)
                            Toast.makeText(ctx, "回收站已清空", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
