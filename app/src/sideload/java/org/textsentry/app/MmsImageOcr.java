package org.textsentry.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.TextRecognizer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** OCR stays on-device. Image bytes are never transmitted to the AI service. */
final class MmsImageOcr {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private MmsImageOcr() {}

    static String read(ContentResolver resolver, Uri uri) {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return "";
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (buffer.size() + count > MAX_BYTES) return "";
                buffer.write(chunk, 0, count);
            }
            byte[] image = buffer.toByteArray();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(image, 0, image.length, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return "";
            int sample = 1;
            while (options.outWidth / sample > 1600 || options.outHeight / sample > 1600) {
                sample *= 2;
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length, options);
            if (bitmap == null) return "";
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            try {
                String result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                        10, TimeUnit.SECONDS).getText();
                return result == null ? "" : result;
            } finally {
                recognizer.close();
                bitmap.recycle();
            }
        } catch (Exception ignored) {
            // If decoding/OCR fails, retain other text without claiming to understand the image.
            return "";
        }
    }
}
