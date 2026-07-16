export interface LatestRequestTicket<Key> {
  sequence: number;
  key: Key;
}

export interface LatestRequestGate<Key> {
  begin: (key: Key) => LatestRequestTicket<Key>;
  isCurrent: (ticket: LatestRequestTicket<Key>, currentKey: Key) => boolean;
  invalidate: () => void;
}

export function createLatestRequestGate<Key>(): LatestRequestGate<Key> {
  let sequence = 0;

  return {
    begin(key) {
      sequence += 1;
      return { sequence, key };
    },
    isCurrent(ticket, currentKey) {
      return ticket.sequence === sequence && Object.is(ticket.key, currentKey);
    },
    invalidate() {
      sequence += 1;
    }
  };
}

export interface AsyncActionLock {
  isLocked: () => boolean;
  run: <Result>(action: () => Promise<Result>) => Promise<Result | undefined>;
}

export function createAsyncActionLock(): AsyncActionLock {
  let locked = false;
  return {
    isLocked() {
      return locked;
    },
    async run(action) {
      if (locked) return undefined;
      locked = true;
      try {
        return await action();
      } finally {
        locked = false;
      }
    }
  };
}
