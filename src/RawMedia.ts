import { NativeModules } from 'react-native';
import MediaStream from './MediaStream';
import MediaStreamError from './MediaStreamError';

const { WebRTCModule } = NativeModules;

type CreateRawStreamResult = {
    streamId: string;
    track: {
        id: string;
        kind: string;
    };
};

const getRawMedia = async (width: number, height: number): Promise<MediaStream> => {
    try {
        const trackInfo = (await WebRTCModule.createRawStream(width, height)) as CreateRawStreamResult;
        const { streamId, track } = trackInfo;
        const info = {
            streamId: streamId,
            streamReactTag: streamId,
            tracks: [track]
        };
        const stream = new MediaStream(info);
        return stream;
    } catch (e: any) {
        throw new MediaStreamError(e);
    }
};

const sendRawFrame = async (
    buffer: string | Uint8Array,
    size: number,
    width: number,
    height: number
): Promise<void> => {
    let stringBuffer = buffer;
    if (buffer instanceof Uint8Array) {
        stringBuffer = buffer.toString();
    }
    await WebRTCModule.sendRawFrame(stringBuffer, size, width, height);
};

export { sendRawFrame, getRawMedia };
