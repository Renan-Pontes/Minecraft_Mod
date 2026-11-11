import socket, struct, sys, json, time

class Rcon:
    def __init__(self, host='127.0.0.1', port=25575, pw='isekai'):
        self.s = socket.create_connection((host, port), timeout=120)
        self.rid = 0
        self._send(3, pw)
        self._recv()
    def _send(self, t, body):
        self.rid += 1
        data = struct.pack('<ii', self.rid, t) + body.encode('utf-8') + b'\x00\x00'
        self.s.sendall(struct.pack('<i', len(data)) + data)
    def _recvn(self, n):
        b = b''
        while len(b) < n:
            c = self.s.recv(n - len(b))
            if not c: raise ConnectionError('closed')
            b += c
        return b
    def _recv(self):
        ln = struct.unpack('<i', self._recvn(4))[0]
        d = self._recvn(ln)
        rid, t = struct.unpack('<ii', d[:8])
        return d[8:-2].decode('utf-8', 'replace')
    def cmd(self, c):
        self._send(2, c)
        return self._recv()

def strip(s):
    import re
    return re.sub('\u00a7.', '', s)

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    r = Rcon()
    for c in sys.argv[1:]:
        print('>', c); print(strip(r.cmd(c)))
