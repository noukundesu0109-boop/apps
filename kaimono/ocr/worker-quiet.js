// tesseract の無害な「Parameter not found」警告だけを黙らせて、本体の worker を読み込む
(function () { var e = console.error, w = console.warn; function q(f) { return function () { var m = String(arguments[0] || ''); if (/^Warning: Parameter not found/.test(m)) return; return f.apply(console, arguments) } } console.error = q(e); console.warn = q(w) })();
importScripts('./worker.min.js');
