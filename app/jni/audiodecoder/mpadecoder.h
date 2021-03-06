#ifndef MPEGAUDIO_DECODER_H
#define MPEGAUDIO_DECODER_H

#include "decoder.h"

extern "C" {
#include "mad.h"
}

class MpegAudioDecoder : public Decoder {
public:

    MpegAudioDecoder();

	virtual ~MpegAudioDecoder();

    int decodeDirect(char* pchInput, int inputSize, int length) {
        return decode(pchInput, 0, length);
    }

	int decode(char* BYTE, int offset, int length);

    bool readDirect(char* pchInput, int inputSize, int length) {
        return read(pchInput, 0, length);
    }

	bool read(char* BYTE, int offset, int length);

	int getChannels() {
		return mChannels;
	}

    int getSampleRate() {
		return mSampleRate;
	}

private:

    void init();

    void finish();

    void prepareBuffer(struct mad_header const *header, struct mad_pcm *pcm);

	int mChannels;

	int mSampleRate;

    struct mad_stream mStream;

    struct mad_frame mFrame;

    struct mad_synth mSynth;

    struct mad_header mHeader;

    uint8_t mInputBuffer[4096];

    int mInputLength;

    int8_t mBuffer[1152*4];
};

#endif // MPEGAUDIO_DECODER_H
