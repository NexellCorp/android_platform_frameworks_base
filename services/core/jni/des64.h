#pragma once

#define DES64_BLOCK_SIZE 128

#define SERIALKEY			{0x37, 0x18, 0x29, 0x14, 0x28, 0x63, 0x93, 0x26}
#define PACKKEY				{0x37, 0x18, 0x29, 0x14, 0x28, 0x63, 0x93, 0x26}
#define SERIALKEY_UMS		{0x28, 0x63, 0x93, 0x26, 0x37, 0x18, 0x29, 0x14}

#define TRUE		1
#define FALSE		0


class CDES64
{

protected:

	typedef	union	{
			unsigned char	b[8];
			short    w[4];
			unsigned int   dw[2];
	} BIT64;

	static char mask[8];

	static void des_decryption(BIT64 key, BIT64* plain, BIT64 *out);
	static void des_encryption(BIT64 key, BIT64* plain, BIT64 *out);
	static void RoundFunction(BIT64 key, BIT64 *text);
	static void MakeKey(BIT64 key, BIT64 keys[16]);
	static void FP(BIT64 *text);
	static void IP(BIT64 *text);
public:
	CDES64();
	~CDES64();

	static BIT64 keySerial, keySerial_UMS, keyPack;

	static int Encode( BIT64 key, unsigned char * src, unsigned char * tgt, int len );
	static int Decode( BIT64 key, unsigned char * src, unsigned char * tgt, int len );
};
