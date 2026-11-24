package com.muhanak.ps2suite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.github.luben.zstd.Zstd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private boolean isDark = true;
    private String currentMode = "zso";
    private Uri selectedFileUri = null;

    private ScrollView rootScroll;
    private LinearLayout headerCard, toolCard, logCard, footerCard;
    private TextView tvBrandTitle, tvBrandSub, tvRightsBadge, tvToolTitle, tvToolDesc, tvStatus, tvConsole, tvFooter;
    private EditText etInputPath, etOutputFolder;
    private Button btnToggleTheme, tabZso, tabArt, tabVcd, tabBin2Iso, btnPickFile, btnStartAction;
    private ProgressBar progressBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    if (selectedFileUri != null) {
                        etInputPath.setText(selectedFileUri.toString());
                        appendLog("[*] تم اختيار الملف: " + selectedFileUri.getLastPathSegment());
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupListeners();
        applyTheme();
    }

    private void bindViews() {
        rootScroll = findViewById(R.id.rootScroll);
        headerCard = findViewById(R.id.headerCard);
        toolCard = findViewById(R.id.toolCard);
        logCard = findViewById(R.id.logCard);
        footerCard = findViewById(R.id.footerCard);
        tvBrandTitle = findViewById(R.id.tvBrandTitle);
        tvBrandSub = findViewById(R.id.tvBrandSub);
        tvRightsBadge = findViewById(R.id.tvRightsBadge);
        tvToolTitle = findViewById(R.id.tvToolTitle);
        tvToolDesc = findViewById(R.id.tvToolDesc);
        tvStatus = findViewById(R.id.tvStatus);
        tvConsole = findViewById(R.id.tvConsole);
        tvFooter = findViewById(R.id.tvFooter);
        etInputPath = findViewById(R.id.etInputPath);
        etOutputFolder = findViewById(R.id.etOutputFolder);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        tabZso = findViewById(R.id.tabZso);
        tabArt = findViewById(R.id.tabArt);
        tabVcd = findViewById(R.id.tabVcd);
        tabBin2Iso = findViewById(R.id.tabBin2Iso);
        btnPickFile = findViewById(R.id.btnPickFile);
        btnStartAction = findViewById(R.id.btnStartAction);
        progressBar = findViewById(R.id.progressBar);

        tvConsole.setMovementMethod(new ScrollingMovementMethod());
        File defaultOut = new File(getExternalFilesDir(null), "PS2_Muhanak_Full");
        etOutputFolder.setText(defaultOut.getAbsolutePath());
    }

    private void setupListeners() {
        btnToggleTheme.setOnClickListener(v -> { isDark = !isDark; applyTheme(); });
        tabZso.setOnClickListener(v -> switchTab("zso"));
        tabArt.setOnClickListener(v -> switchTab("art"));
        tabVcd.setOnClickListener(v -> switchTab("vcd"));
        tabBin2Iso.setOnClickListener(v -> switchTab("bin2iso"));
        btnPickFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            filePickerLauncher.launch(intent);
        });
        btnStartAction.setOnClickListener(v -> runCurrentTool());
    }

    private void switchTab(String mode) {
        currentMode = mode;
        selectedFileUri = null;
        etInputPath.setText("");
        int normalCardBg = isDark ? R.drawable.bg_card_dark : R.drawable.bg_card_light;
        int normalText = isDark ? Color.parseColor("#E2E8F0") : Color.parseColor("#1E293B");
        for (Button b : new Button[]{tabZso, tabArt, tabVcd, tabBin2Iso}) {
            b.setBackgroundResource(normalCardBg);
            b.setTextColor(normalText);
        }
        if (mode.equals("zso")) {
            tabZso.setBackgroundResource(R.drawable.bg_btn_gradient);
            tabZso.setTextColor(Color.WHITE);
            tvToolTitle.setText("🗜️ ضغط ألعاب PS2 من ISO إلى ZSO");
            tvToolDesc.setText("يضغط ملفات ISO بكتل 16KB باستخدام Zstandard لإنتاج ملف .zso.");
            etInputPath.setHint("اختر ملف .ISO ...");
        } else if (mode.equals("art")) {
            tabArt.setBackgroundResource(R.drawable.bg_btn_gradient);
            tabArt.setTextColor(Color.WHITE);
            tvToolTitle.setText("🖼️ مدير أغلفة ART (أسلوب OPL Manager)");
            tvToolDesc.setText("اكتب كود اللعبة مثل SLUS_209.46 لسحب جميع صور الغلاف والخلفية.");
            etInputPath.setHint("أدخل كود اللعبة (مثال: SLUS_209.46)...");
        } else if (mode.equals("vcd")) {
            tabVcd.setBackgroundResource(R.drawable.bg_btn_gradient);
            tabVcd.setTextColor(Color.WHITE);
            tvToolTitle.setText("💿 تحويل PS1 من BIN إلى VCD (POPStarter)");
            tvToolDesc.setText("يحول لعبة PS1 إلى VCD ويجهز مجلد POPS.");
            etInputPath.setHint("اختر ملف PS1 بصيغة .BIN ...");
        } else if (mode.equals("bin2iso")) {
            tabBin2Iso.setBackgroundResource(R.drawable.bg_btn_gradient);
            tabBin2Iso.setTextColor(Color.WHITE);
            tvToolTitle.setText("📀 محول WinBin2Iso — تحويل PS2 BIN إلى ISO");
            tvToolDesc.setText("يستخلص بيانات ISO القياسية (2048 بايت/قطاع) من أقراص BIN.");
            etInputPath.setHint("اختر ملف .BIN ...");
        }
    }

    private void applyTheme() {
        if (isDark) {
            rootScroll.setBackgroundColor(Color.parseColor("#060913"));
            headerCard.setBackgroundResource(R.drawable.bg_card_dark);
            toolCard.setBackgroundResource(R.drawable.bg_card_dark);
            logCard.setBackgroundResource(R.drawable.bg_card_dark);
            tvBrandTitle.setTextColor(Color.parseColor("#38BDF8"));
            tvBrandSub.setTextColor(Color.parseColor("#94A3B8"));
            tvToolTitle.setTextColor(Color.parseColor("#38BDF8"));
            tvToolDesc.setTextColor(Color.parseColor("#94A3B8"));
            etInputPath.setTextColor(Color.parseColor("#F8FAFC"));
            etOutputFolder.setTextColor(Color.parseColor("#F8FAFC"));
            tvConsole.setBackgroundColor(Color.parseColor("#04070D"));
            tvConsole.setTextColor(Color.parseColor("#34D399"));
            btnToggleTheme.setText("☀️ فاتح");
        } else {
            rootScroll.setBackgroundColor(Color.parseColor("#EEF3F9"));
            headerCard.setBackgroundResource(R.drawable.bg_card_light);
            toolCard.setBackgroundResource(R.drawable.bg_card_light);
            logCard.setBackgroundResource(R.drawable.bg_card_light);
            tvBrandTitle.setTextColor(Color.parseColor("#1E40AF"));
            tvBrandSub.setTextColor(Color.parseColor("#475569"));
            tvToolTitle.setTextColor(Color.parseColor("#1E40AF"));
            tvToolDesc.setTextColor(Color.parseColor("#475569"));
            etInputPath.setTextColor(Color.parseColor("#0F172A"));
            etOutputFolder.setTextColor(Color.parseColor("#0F172A"));
            tvConsole.setBackgroundColor(Color.parseColor("#F1F5F9"));
            tvConsole.setTextColor(Color.parseColor("#0F172A"));
            btnToggleTheme.setText("🌙 داكن");
        }
        switchTab(currentMode);
    }

    private void runCurrentTool() {
        String inputVal = etInputPath.getText().toString().trim();
        String outDirPath = etOutputFolder.getText().toString().trim();
        if (inputVal.isEmpty()) {
            Toast.makeText(this, "يرجى اختيار ملف أو إدخال كود اللعبة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }
        File outDir = new File(outDirPath);
        if (!outDir.exists()) outDir.mkdirs();
        progressBar.setProgress(0);
        executor.execute(() -> {
            try {
                if (currentMode.equals("art")) downloadOplArt(inputVal.toUpperCase(), new File(outDir, "ART"));
                else if (currentMode.equals("vcd")) convertBinToVcd(selectedFileUri, new File(outDir, "POPS"));
                else if (currentMode.equals("bin2iso")) convertWinBin2Iso(selectedFileUri, outDir);
                else if (currentMode.equals("zso")) compressIsoToZso(selectedFileUri, outDir);
            } catch (Exception e) {
                appendLog("[خطأ] " + e.getMessage());
                updateProgress(0, "حدث خطأ أثناء التنفيذ");
            }
        });
    }

    private void compressIsoToZso(Uri isoUri, File outDir) throws Exception {
        if (isoUri == null) throw new Exception("اختر ملف .ISO من زر الاختيار أولاً");
        File outZso = new File(outDir, "GAME_PS2_MUHANAK.zso");
        int blockSize = 16384;
        int level = 15;
        appendLog("[*] بدء ضغط ISO إلى ZSO بمستوى " + level + "...");
        try (InputStream in = getContentResolver().openInputStream(isoUri);
             RandomAccessFile raf = new RandomAccessFile(outZso, "rw")) {
            byte[] header = new byte[20];
            java.util.ArrayList<Long> indexList = new java.util.ArrayList<>();
            raf.write(header);
            long indexStart = raf.getFilePointer();
            byte[] chunk = new byte[blockSize];
            long curOffset = indexStart, processed = 0;
            while (true) {
                int read = readFullSector(in, chunk);
                if (read <= 0) break;
                byte[] inputBlock = (read == blockSize) ? chunk : java.util.Arrays.copyOf(chunk, read);
                byte[] comp = Zstd.compress(inputBlock, level);
                byte[] payload;
                long entry;
                if (Zstd.isError(comp.length) || comp.length >= inputBlock.length) {
                    payload = inputBlock;
                    entry = curOffset | (1L << 63);
                } else {
                    payload = comp;
                    entry = curOffset;
                }
                indexList.add(entry);
                raf.write(payload);
                curOffset += payload.length;
                processed += read;
                if (processed % (1024 * 1024 * 8) == 0) updateProgress(50, "تم ضغط " + (processed / (1024 * 1024)) + " MB...");
            }
            indexList.add(curOffset);
            long idxBytesLen = indexList.size() * 8L;
            byte[] payloadData = new byte[(int) Math.min(curOffset - indexStart, Integer.MAX_VALUE)];
            raf.seek(indexStart);
            raf.readFully(payloadData);
            raf.seek(indexStart);
            ByteBuffer idxBuf = ByteBuffer.allocate((int) idxBytesLen).order(ByteOrder.LITTLE_ENDIAN);
            for (long val : indexList) {
                long adjusted = (val & (1L << 63)) != 0 ? ((val & ~(1L << 63)) + idxBytesLen) | (1L << 63) : (val + idxBytesLen);
                idxBuf.putLong(adjusted);
            }
            raf.write(idxBuf.array());
            raf.write(payloadData);
            raf.seek(0);
            ByteBuffer hdrBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            hdrBuf.putInt(0, 0x004F535A);
            hdrBuf.putLong(4, processed);
            hdrBuf.putInt(12, blockSize);
            hdrBuf.putInt(16, indexList.size() - 1);
            raf.write(hdrBuf.array());
        }
        updateProgress(100, "اكتمل ضغط ZSO ✓");
        appendLog("[✓] تم حفظ ZSO في: " + outZso.getAbsolutePath());
    }

    private void downloadOplArt(String gameId, File artDir) throws Exception {
        if (!artDir.exists()) artDir.mkdirs();
        String[] suffixes = {"_COV", "_COV2", "_BG", "_ICO", "_LAB", "_LGO", "_SCR", "_SCR2"};
        int saved = 0;
        for (int i = 0; i < suffixes.length; i++) {
            String fileName = gameId + suffixes[i] + ".png";
            String urlStr = "https://raw.githubusercontent.com/xlenore/ps2-covers/main/covers/default/" + fileName;
            appendLog("[*] فحص: " + fileName);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10000);
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(new File(artDir, fileName))) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }
                saved++;
                appendLog("[✓] تم حفظ: " + fileName);
            }
            int pct = (int) (((i + 1) / (float) suffixes.length) * 100);
            updateProgress(pct, "سحب ART: " + pct + "%");
        }
        appendLog("[✓] اكتمل سحب ART! إجمالي الصور المحفوظة: " + saved);
    }

    private void convertBinToVcd(Uri binUri, File popsDir) throws Exception {
        if (binUri == null) throw new Exception("اختر ملف .BIN من زر الاختيار أولاً");
        if (!popsDir.exists()) popsDir.mkdirs();
        File outVcd = new File(popsDir, "GAME_PS1_MUHANAK.VCD");
        byte[] header = new byte[0x100000];
        header[0] = 'V'; header[1] = 'C'; header[2] = 'D'; header[3] = 0;
        appendLog("[*] جاري تحويل PS1 BIN إلى VCD...");
        try (InputStream in = getContentResolver().openInputStream(binUri);
             OutputStream out = new FileOutputStream(outVcd)) {
            out.write(header);
            byte[] buf = new byte[65536];
            int len;
            long total = 0;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                total += len;
                if (total % (1024 * 1024 * 5) == 0) updateProgress(50, "تم نسخ " + (total / (1024 * 1024)) + " MB...");
            }
        }
        updateProgress(100, "اكتمل التحويل إلى VCD ✓");
        appendLog("[✓] تم الحفظ في: " + outVcd.getAbsolutePath());
    }

    private void convertWinBin2Iso(Uri binUri, File outDir) throws Exception {
        if (binUri == null) throw new Exception("اختر ملف .BIN من زر الاختيار أولاً");
        File outIso = new File(outDir, "CONVERTED_PS2_MUHANAK.iso");
        appendLog("[*] جاري تحويل BIN إلى ISO (قطاعات 2352 -> 2048)...");
        try (InputStream in = getContentResolver().openInputStream(binUri);
             OutputStream out = new FileOutputStream(outIso)) {
            byte[] sector = new byte[2352];
            long count = 0;
            while (readFullSector(in, sector) == 2352) {
                out.write(sector, 24, 2048);
                count++;
                if (count % 4096 == 0) updateProgress(60, "معالجة القطاعات: " + count);
            }
        }
        updateProgress(100, "اكتمل التحويل إلى ISO ✓");
        appendLog("[✓] تم الحفظ في: " + outIso.getAbsolutePath());
    }

    private int readFullSector(InputStream in, byte[] buf) throws Exception {
        int total = 0;
        while (total < buf.length) {
            int r = in.read(buf, total, buf.length - total);
            if (r == -1) break;
            total += r;
        }
        return total;
    }

    private void updateProgress(int pct, String status) {
        runOnUiThread(() -> { progressBar.setProgress(pct); tvStatus.setText(status); });
    }

    private void appendLog(String msg) {
        runOnUiThread(() -> tvConsole.append(msg + "\n"));
    }
}
