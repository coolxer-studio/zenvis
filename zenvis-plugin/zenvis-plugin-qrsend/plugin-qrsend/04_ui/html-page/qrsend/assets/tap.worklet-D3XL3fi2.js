// Runs on the audio thread: passes the microphone's samples to the page.
class Tap extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0]?.[0];
    if (channel) this.port.postMessage(channel.slice());
    return true;
  }
}
registerProcessor("tap", Tap);
