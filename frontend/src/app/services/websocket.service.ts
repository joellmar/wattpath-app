import { map, Observable } from 'rxjs';
import { ReadingResponse } from '../interfaces/reading-response.interface';
import { RxStomp } from '@stomp/rx-stomp';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class WebsocketService {
  private readonly rxStomp = new RxStomp();

  constructor() {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/ws-iot`;

    this.rxStomp.configure({
      brokerURL: wsUrl,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 20000,
      reconnectDelay: 5000,
      debug: (msg: string) => {
        console.log(new Date(), msg);
      },
    });

    this.rxStomp.activate();
  }

  /**
   * Escucha las lecturas de un dispositivo específico en tiempo real
   * @param macAddress La dirección MAC del enchufe inteligente
   */
  watchReadings(macAddress: string): Observable<ReadingResponse> {
    const destination = `/topic/readings/${macAddress}`;

    return this.rxStomp.watch(destination).pipe(
      map(message => JSON.parse(message.body) as ReadingResponse)
    );
  }
}
