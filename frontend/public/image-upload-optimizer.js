(() => {
  'use strict';

  const INPUT_ID = 'product-image';
  const HELP_ID = 'product-image-help';
  const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png']);
  const SERVER_MAX_BYTES = 5 * 1024 * 1024;
  const TARGET_MAX_BYTES = Math.floor(4.5 * 1024 * 1024);
  const MAX_SOURCE_BYTES = 40 * 1024 * 1024;
  const SERVER_MAX_PIXELS = 10_000_000;
  const DISPLAY_MAX_DIMENSION = 1600;
  const MIN_PNG_DIMENSION = 720;
  const replayedInputs = new WeakSet();

  const defaultHelpText =
    'JPEG o PNG. Hasta 6 imágenes por producto. Las fotos grandes se optimizan automáticamente antes de subir.';

  function updateHelp(message = defaultHelpText) {
    const help = document.getElementById(HELP_ID);
    if (help) help.textContent = message;
  }

  function updateRenderedHelp() {
    const input = document.getElementById(INPUT_ID);
    if (input instanceof HTMLInputElement) updateHelp();
  }

  function replaceExtension(name, type) {
    const extension = type === 'image/png' ? 'png' : 'jpg';
    const stem = name.replace(/\.[^.]+$/, '');
    return `${stem || 'producto'}.${extension}`;
  }

  async function decodeImage(file) {
    if ('createImageBitmap' in globalThis) {
      try {
        const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
        return {
          width: bitmap.width,
          height: bitmap.height,
          draw: (context, width, height) => context.drawImage(bitmap, 0, 0, width, height),
          close: () => bitmap.close(),
        };
      } catch {
        try {
          const bitmap = await createImageBitmap(file);
          return {
            width: bitmap.width,
            height: bitmap.height,
            draw: (context, width, height) => context.drawImage(bitmap, 0, 0, width, height),
            close: () => bitmap.close(),
          };
        } catch {
          // Fall through to the HTMLImageElement implementation.
        }
      }
    }

    const url = URL.createObjectURL(file);
    try {
      const image = new Image();
      image.decoding = 'async';
      image.src = url;
      await image.decode();
      return {
        width: image.naturalWidth,
        height: image.naturalHeight,
        draw: (context, width, height) => context.drawImage(image, 0, 0, width, height),
        close: () => URL.revokeObjectURL(url),
      };
    } catch (error) {
      URL.revokeObjectURL(url);
      throw error;
    }
  }

  function targetDimensions(width, height, maxDimension) {
    const scale = Math.min(1, maxDimension / Math.max(width, height));
    return {
      width: Math.max(1, Math.round(width * scale)),
      height: Math.max(1, Math.round(height * scale)),
    };
  }

  function canvasBlob(source, width, height, type, quality) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d', { alpha: type === 'image/png' });
    if (!context) throw new Error('No pudimos preparar el optimizador de imágenes.');

    if (type === 'image/jpeg') {
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, width, height);
    }
    source.draw(context, width, height);

    return new Promise((resolve, reject) => {
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('No pudimos comprimir la imagen.'))),
        type,
        quality,
      );
    });
  }

  async function encodeOptimized(source, type) {
    let maxDimension = DISPLAY_MAX_DIMENSION;
    let quality = type === 'image/jpeg' ? 0.86 : undefined;

    for (let attempt = 0; attempt < 8; attempt += 1) {
      const size = targetDimensions(source.width, source.height, maxDimension);
      const blob = await canvasBlob(source, size.width, size.height, type, quality);
      if (blob.size <= TARGET_MAX_BYTES) return blob;

      if (type === 'image/jpeg' && quality > 0.64) {
        quality = Math.max(0.64, quality - 0.08);
      } else {
        maxDimension = Math.max(MIN_PNG_DIMENSION, Math.floor(maxDimension * 0.85));
      }
    }

    const finalSize = targetDimensions(source.width, source.height, maxDimension);
    const finalBlob = await canvasBlob(source, finalSize.width, finalSize.height, type, quality);
    if (finalBlob.size > SERVER_MAX_BYTES) {
      throw new Error('La imagen sigue siendo demasiado pesada después de optimizarla.');
    }
    return finalBlob;
  }

  async function optimizeFile(file) {
    if (!ALLOWED_TYPES.has(file.type)) return file;
    if (file.size > MAX_SOURCE_BYTES) {
      throw new Error('La imagen original no puede superar 40 MiB.');
    }

    const source = await decodeImage(file);
    try {
      const pixels = source.width * source.height;
      const needsOptimization =
        file.size > SERVER_MAX_BYTES ||
        pixels > SERVER_MAX_PIXELS ||
        Math.max(source.width, source.height) > DISPLAY_MAX_DIMENSION;

      if (!needsOptimization) return file;

      const blob = await encodeOptimized(source, file.type);
      return new File([blob], replaceExtension(file.name, file.type), {
        type: file.type,
        lastModified: file.lastModified,
      });
    } finally {
      source.close();
    }
  }

  async function optimizeSelection(input, files) {
    const plural = files.length === 1 ? 'imagen' : 'imágenes';
    updateHelp(`Optimizando ${files.length} ${plural}…`);
    input.setAttribute('aria-busy', 'true');
    input.disabled = true;

    try {
      const optimized = [];
      for (let index = 0; index < files.length; index += 1) {
        updateHelp(`Optimizando imagen ${index + 1} de ${files.length}…`);
        optimized.push(await optimizeFile(files[index]));
      }

      const transfer = new DataTransfer();
      optimized.forEach((file) => transfer.items.add(file));
      input.files = transfer.files;
      replayedInputs.add(input);
      input.disabled = false;
      input.removeAttribute('aria-busy');
      input.dispatchEvent(new Event('change', { bubbles: true }));
      updateHelp();
    } catch (error) {
      console.error('Comercio Flex no pudo optimizar la imagen seleccionada.', error);
      input.disabled = false;
      input.removeAttribute('aria-busy');
      input.value = '';
      updateHelp(
        error instanceof Error
          ? `${error.message} Probá con otra imagen.`
          : 'No pudimos optimizar la imagen. Probá con otra imagen.',
      );
    }
  }

  document.addEventListener(
    'change',
    (event) => {
      const input = event.target;
      if (!(input instanceof HTMLInputElement) || input.id !== INPUT_ID || input.type !== 'file') {
        return;
      }
      if (replayedInputs.has(input)) {
        replayedInputs.delete(input);
        return;
      }

      const files = Array.from(input.files ?? []);
      if (!files.length || files.some((file) => !ALLOWED_TYPES.has(file.type))) return;

      event.stopImmediatePropagation();
      void optimizeSelection(input, files);
    },
    true,
  );

  const observer = new MutationObserver(updateRenderedHelp);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  updateRenderedHelp();
})();
