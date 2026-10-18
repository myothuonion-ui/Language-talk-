class PcmCapture extends AudioWorkletProcessor {
  constructor() {
    super();
    this.buffer = new Int16Array(1600);
    this.offset = 0;
  }
  process(inputs) {
    const input = inputs[0]?.[0];
    if (!input) return true;
    for (const sample of input) {
      this.buffer[this.offset++] = Math.max(
        -32768,
        Math.min(32767, Math.round(sample * 32767)),
      );
      if (this.offset === this.buffer.length) {
        this.port.postMessage(this.buffer.buffer);
        this.buffer = new Int16Array(1600);
        this.offset = 0;
      }
    }
    return true;
  }
}
registerProcessor("pcm-capture", PcmCapture);
