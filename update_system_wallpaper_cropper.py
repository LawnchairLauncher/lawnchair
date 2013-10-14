# This script is used to push the most up-to-date files from
# Launcher into frameworks' version of the WallpaperCropActivity
# (and supporting files)
# The framework versions have some small modifications that are
# necessary so do this with care
import os
import sys
files = """
src/android/util/Pools.java
src/com/android/gallery3d/util/IntArray.java
src/com/android/gallery3d/common/Utils.java
src/com/android/gallery3d/exif/ByteBufferInputStream.java
src/com/android/gallery3d/exif/CountedDataInputStream.java
src/com/android/gallery3d/exif/ExifData.java
src/com/android/gallery3d/exif/ExifInterface.java
src/com/android/gallery3d/exif/ExifInvalidFormatException.java
src/com/android/gallery3d/exif/ExifModifier.java
src/com/android/gallery3d/exif/ExifOutputStream.java
src/com/android/gallery3d/exif/ExifParser.java
src/com/android/gallery3d/exif/ExifReader.java
src/com/android/gallery3d/exif/ExifTag.java
src/com/android/gallery3d/exif/IfdData.java
src/com/android/gallery3d/exif/IfdId.java
src/com/android/gallery3d/exif/JpegHeader.java
src/com/android/gallery3d/exif/OrderedDataOutputStream.java
src/com/android/gallery3d/exif/Rational.java
src/com/android/gallery3d/glrenderer/BasicTexture.java
src/com/android/gallery3d/glrenderer/BitmapTexture.java
src/com/android/gallery3d/glrenderer/GLCanvas.java
src/com/android/gallery3d/glrenderer/GLES20Canvas.java
src/com/android/gallery3d/glrenderer/GLES20IdImpl.java
src/com/android/gallery3d/glrenderer/GLId.java
src/com/android/gallery3d/glrenderer/GLPaint.java
src/com/android/gallery3d/glrenderer/RawTexture.java
src/com/android/gallery3d/glrenderer/Texture.java
src/com/android/gallery3d/glrenderer/UploadedTexture.java
src/com/android/photos/BitmapRegionTileSource.java
src/com/android/photos/views/BlockingGLTextureView.java
src/com/android/photos/views/TiledImageRenderer.java
src/com/android/photos/views/TiledImageView.java
src/com/android/gallery3d/common/BitmapUtils.java
src/com/android/launcher3/CropView.java
src/com/android/launcher3/WallpaperCropActivity.java
"""

if len(sys.argv) != 2:
    print "Usage: python update_sytem_wallpaper_cropper.py <framework_dir>"
    exit()
framework_dir = sys.argv[1] + "/packages/WallpaperCropper"
for file_path in files.split():
    dir = os.path.dirname(file_path)
    dir = dir.replace("launcher3", "wallpapercropper")
    cmd = 'cp %s %s/%s' % (file_path, framework_dir, dir)
    print cmd
    os.system(cmd)
