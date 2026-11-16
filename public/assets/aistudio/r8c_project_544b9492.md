# R8C Programmer Project

## File: area.hpp
```cpp
#pragma once
//=====================================================================//
/*!	@file
	@brief	領域クラス
    @author 平松邦仁 (hira@rvf-rc45.net)
	@copyright	Copyright (C) 2017 Kunihito Hiramatsu @n
				Released under the MIT license @n
				https://github.com/hirakuni45/R8C/blob/master/LICENSE
*/
//=====================================================================//
#include <vector>

namespace utils {

	struct area_t {
		uint32_t	org_;
		uint32_t	end_;
		area_t(uint32_t o = 0, uint32_t e = 0) : org_(o), end_(e) { }

		bool is_in(uint32_t adr) const {
			if(org_ <= adr && adr <= end_) return true;
			else return false;
		}
	};

	typedef std::vector<area_t>	areas;
}
```

## File: r8c_prog.conf
```ini
################################################################
#	r8c_prog configuration
#
#	2010.3.21	KAWAKAMI Yukio
#   2015.5.14   HIRAMATSU Kunihito
################################################################

[DEFAULT]

programmer = Generic

device=R5F2M120

# 標準のシリアルポート、機器依存ポート名
# port_win, port_osx, port_linux は、起動しているシステム用に認識し、port より優先されます。
port_win   = COM7
port_osx   = /dev/tty.usbserial-DA00X2QP
port_linux = /dev/ttyUSB0
#port = /dev/ttyS8
#port = COM11

speed = 115200
#speed = 57600
#speed = 38400
#speed = 19200

#file = TEST.mot

#id-file =
#id = FF:FF:FF:FF:FF:FF:FF


[PROGRAMMER]

Generic {
	comment = "Generic Serial I/F or USB-Serial"
}

R8Cprog162 {
	comment = "r8c_prog programmer by Yuki/Hira"
}


[DEVICE]

R5F21246 {
	group = "R8C/24"
	rom = 32k
	ram = 2k
	comment = "; R5F21246"
	rom-area =	8000,BFFF,
				C000,FFFF
}

R5F21256 {
	group = "R8C/25"
	rom = 32k
	data = 2k
	ram = 2k
	comment = "; R5F21256"
	rom-area =	8000,BFFF,
				C000,FFFF
	data-area =	2400,27FF,
				2800,2BFF
}

R5F21262 {
	group = "R8C/26"
	rom = 8k
	ram = 512
	comment = "; R5F21262"
	rom-area =	E000,FFFF
}

R5F21266 {
	group = "R8C/26"
	rom = 32k
	ram = 1.5k
	comment = "; R5F21266"
	rom-area =	8000,BFFF,
				C000,FFFF
}

R5F21276 {
	group = "R8C/27"
	rom = 32k
	data = 2k
	ram = 1.5k
	comment = "; R5F21276"
	rom-area =	8000,BFFF,
				C000,FFFF
	data-area =	2400,27FF,
				2800,2BFF
}

R5F21294 {
	group = "R8C/29"
	rom = 16k
	data = 2k
	ram = 1k
	comment = "; R5F21294"
	rom-area =	C000,FFFF
	data-area =	2400,27FF,
				2800,2BFF
}

R5F212AA {
	group = "R8C/2A"
	rom = 96k
	ram = 7k
	comment = "; R5F212AA"

	rom-area =	4000,BFFF,
				C000,13FFF,
				14000,1BFFF
}

R5F212BA {
	group = "R8C/2B"
	rom = 96k
	data = 2k
	ram = 7k
	comment = "; R5F212BA"

	rom-area =	4000,BFFF,
				C000,13FFF,
				14000,1BFFF

	data-area =	2400,27FF,
				2800,2BFF
}

R5F212DC {
	group = "R8C/2D"
	rom = 128k
	data = 2k
	ram = 7.5k
	comment = "; R5F212DC"

	rom-area =	4000,BFFF,
				C000,13FFF,
				14000,1BFFF,
				1C000,23FFF

	data-area =	2400,27FF,
				2800,2BFF
}


R5F21354 {
	group = "R8C/35A"
	rom = 16k
	data = 4k
	ram = 1.5k
	comment = "; R5F21354ANFP"

	rom-area = C000,FFFF

	data-area = 3000,3FFFF
}

################################################################

R5F2M110 {
	group = "R8C/M11A"
	rom = 64k
	data = 2k
	ram = 1280
	comment = "; R5F2M11A"
	rom-area =	8000,8FFF,
		9000,9FFF,
		A000,AFFF,
		B000,BFFF,
		C000,CFFF,
		D000,DFFF,
		E000,EFFF,
		F000,FFFF,
		10000,10FFF,
		11000,11FFF,
		12000,12FFF,
		13000,13FFF,
		14000,14FFF,
		15000,15FFF,
		16000,16FFF,
		17000,17FFF

	data-area = 3000,33FF,
		 3400,37FF
}

R5F2M120 {
	group = "R8C/M12A"
	rom = 64k
	data = 2k
	ram = 1280
	comment = "; R5F2M12A"
	rom-area =	8000,8FFF,
		9000,9FFF,
		A000,AFFF,
		B000,BFFF,
		C000,CFFF,
		D000,DFFF,
		E000,EFFF,
		F000,FFFF,
		10000,10FFF,
		11000,11FFF,
		12000,12FFF,
		13000,13FFF,
		14000,14FFF,
		15000,15FFF,
		16000,16FFF,
		17000,17FFF

	data-area = 3000,33FF,
	     3400,37FF
}
```

## File: r8c_protocol.hpp
```cpp
#pragma once
//=====================================================================//
/*!	@file
	@brief	R8C プログラミング・プロトコル・クラス
    @author 平松邦仁 (hira@rvf-rc45.net)
	@copyright	Copyright (C) 2017 Kunihito Hiramatsu @n
				Released under the MIT license @n
				https://github.com/hirakuni45/R8C/blob/master/LICENSE
*/
//=====================================================================//
#include "rs232c_io.hpp"
#include <iostream>

namespace r8c {

	//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
	/*!
		@brief	R8C プログラミング・プロトコル・クラス
	*/
	//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
	class protocol {
	public:
		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		/*!
			@brief	status 構造体
		*/
		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		struct status {
			uint8_t	SRD;
			uint8_t SRD1;
			status() : SRD(0), SRD1(0) { }
			int get_id_state() const { return (SRD1 >> 2) & 3; }
			bool get_SR4() const { return (SRD >> 4) & 1; }
			bool get_SR5() const { return (SRD >> 5) & 1; }
		};


		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		/*!
			@brief	ID 構造体
		*/
		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		struct id_t {
			uint8_t	buff[8];
			id_t() { buff[7] = 0; }
			void fill() {
				for(int i = 0; i < 7; ++i) buff[i] = 0xff;
			}
			void clear() {
				for(int i = 0; i < 7; ++i) buff[i] = 0x00;
			}
//			const char* str() const { return static_cast<const char*>(&buff[0]); }
		};


	private:
		utils::rs232c_io	rs232c_;
		timeval 			tv_;

		bool			connection_;
		bool			verification_;

		uint32_t	baud_rate_;

		bool command_(uint8_t cmd) {
			bool f = rs232c_.send(static_cast<char>(cmd));
			rs232c_.sync_send();
			return f;
		}


		bool read_(void* dst, uint32_t length) {
			timeval tv;
			tv.tv_sec  = 0;
			tv.tv_usec = 500000;
			uint32_t len = rs232c_.recv(dst, length, tv);
			return len == length;
		}

	public:
		//-----------------------------------------------------------------//
		/*!
			@brief	コンストラクター
		*/
		//-----------------------------------------------------------------//
		protocol() : connection_(false), verification_(false), baud_rate_(0) { }


		//-----------------------------------------------------------------//
		/*!
			@brief	開始
			@param[in]	path	シリアルデバイスパス
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool start(const std::string& path) {
			if(!rs232c_.open(path, B9600)) {
				return false;
			}

			if(!rs232c_.enable_RTS(false)) {
				return false;
			}
			if(!rs232c_.enable_DTR(false)) {
				return false;
			}

			tv_.tv_sec  = 1;
			tv_.tv_usec = 0;

			connection_ = false;
			verification_ = false;

			return true;
		}


		void recv_out(int num) {
			for(int i = 0; i < num; ++i) {
				int ch = rs232c_.recv(tv_);
				if(ch > 0) {
					std::cout << static_cast<char>(ch);
				}
			}
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	コネクションの確立
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool connection() {
			for(int i = 0; i < 16; ++i) {
				if(!command_(0x00)) {
					return false;
				}
				usleep(20000);	// 20[ms]
			}

			if(!command_(0xB0)) {
				return false;
			}

			timeval tv;
			tv.tv_sec  = 1;
			tv.tv_usec = 0;
			int ch = rs232c_.recv(tv);
			if(ch != 0xB0) {
				return false;
			}
			connection_ = true;
			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	接続速度を変更する
			@param[in]	brate	ボーレート
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool change_speed(speed_t brate) {
			if(!connection_) return false;

			int cmd;
			switch(brate) {
			case B9600:
				cmd = 0xB0;
				baud_rate_ = 9600;
				break;
			case B19200:
				cmd = 0xB1;
				baud_rate_ = 19200;
				break;
			case B38400:
				cmd = 0xB2;
				baud_rate_ = 38400;
				break;
			case B57600:
				cmd = 0xB3;
				baud_rate_ = 57600;
				break;
			case B115200:
				cmd = 0xB4;
				baud_rate_ = 115200;
				break;
			default:
				return false;
			}
			if(!command_(cmd)) {
				return false;
			}
			int ch = rs232c_.recv(tv_);
			if(ch != cmd) {
				return false;
			}

			if(!rs232c_.change_speed(brate)) {
				return false;
			}

			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	バージョン情報の取得
			@return バージョンを返す（エラーならempty）
		*/
		//-----------------------------------------------------------------//
		std::string get_version() {
			if(!connection_) return std::string();

			if(!command_(0xFB)) {
				return std::string();
			}
			char buff[9];
			if(!read_(buff, 8)) {
				return std::string();
			}
			buff[8] = 0;
			return std::string(buff);
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	ステータス情報の取得
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool get_status(status& st) {
			if(!connection_) return false;

			if(!command_(0x70)) {
				return false;
			}

			char buff[2];
			if(!read_(buff, 2)) {
				return false;
			}

			st.SRD  = buff[0];
			st.SRD1 = buff[1];

			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	ステータス情報のクリア
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool clear_status() {
			if(!connection_) return false;

			if(!command_(0x50)) {
				return false;
			}

			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	ID 検査
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool id_inspection(const id_t& t) {
			if(!connection_) return false;

			uint8_t buff[12];
			buff[0] = 0xF5;
			buff[1] = 0xDF;
			buff[2] = 0xFF;
			buff[3] = 0x00;
			buff[4] = 0x07;
			for(int i = 0; i < 7; ++i) buff[5 + i] = t.buff[i];
			if(rs232c_.send(buff, 12) != 12) {
				return false;
			}
			rs232c_.sync_send();

			status st;
			if(!get_status(st)) {
				return false;
			}
			if(st.get_id_state() != 3) {
				return false;
			}

			verification_ = true;

			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	リード・ページ
			@param[in]	address	アドレス
			@param[out]	dst	リード・データ
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool read_page(uint32_t address, uint8_t* dst) {
			if(!connection_) return false;
			if(!verification_) return false;

			uint8_t buff[3];
			buff[0] = 0xFF;
			buff[1] = (address >> 8) & 0xff;
			buff[2] = (address >> 16) & 0xff;
			if(rs232c_.send(buff, 3) != 3) {
				return false;
			}
			rs232c_.sync_send();

			// ボーレートから想定される実時間の２倍
			// 1.0f / static_cast<float>(baud_rate) * 10.0f * 256.0f / 1e-6 * 2.0f;
			return read_(dst, 256);
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	ライト・ページ
			@param[in]	address	アドレス
			@param[in]	src	ライト・データ
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool write_page(uint32_t address, const uint8_t* src) {
			if(!connection_) return false;
			if(!verification_) return false;

			char buff[3];
			buff[0] = 0x41;
			buff[1] = (address >> 8) & 0xff;
			buff[2] = (address >> 16) & 0xff;
			if(rs232c_.send(buff, 3) != 3) {
				return false;
			}
			rs232c_.sync_send();

			if(rs232c_.send(src, 256) != 256) {
				return false;
			}
			rs232c_.sync_send();

			status st;
			if(!get_status(st)) {
				return false;
			}
			if(st.get_SR4() != 0) {
				return false;
			}

			return clear_status();
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	イレース・ページ
			@param[in]	address	アドレス
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool erase_page(uint32_t address) {
			if(!connection_) return false;
			if(!verification_) return false;

			char buff[4];
			buff[0] = 0x20;
			buff[1] = (address >> 8) & 0xff;
			buff[2] = (address >> 16) & 0xff;
			buff[3] = 0xD0;
			if(rs232c_.send(buff, 4) != 4) {
				return false;
			}
			rs232c_.sync_send();

			status st;
			if(!get_status(st)) {
				return false;
			}
			if(st.get_SR5() != 0) {
				return false;
			}

			return clear_status();
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	終了
			@return エラー無ければ「true」
		*/
		//-----------------------------------------------------------------//
		bool end() {
			connection_ = false;
			verification_ = false;
			return rs232c_.close();
		}

	};

}
```

## File: r8c_prog.hpp
```cpp
#pragma once
//=====================================================================//
/*!	@file
	@brief	R8C programmer クラス
    @author 平松邦仁 (hira@rvf-rc45.net)
	@copyright	Copyright (C) 2017, 2023 Kunihito Hiramatsu @n
				Released under the MIT license @n
				https://github.com/hirakuni45/R8C/blob/master/LICENSE
*/
//=====================================================================//
#include "r8c_protocol.hpp"
#include "string_utils.hpp"
#include <set>
#include <boost/format.hpp>

//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
/*!
	@brief	r8c_prog クラス
 */
//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
class r8c_prog {
	bool	verbose_;
	bool	progress_;

	r8c::protocol	proto_;
	std::string		ver_;
	r8c::protocol::id_t	id_;
	std::set<uint32_t>	set_;

public:
	r8c_prog(bool verbose, bool progress) : verbose_(verbose), progress_(progress) {
		id_.fill();
	}

	bool get_progress() const { return progress_; }

	const r8c::protocol::id_t& get_id() const { return id_; }

	bool set_id(const std::string& text) {
		utils::strings ss = utils::split_text(text, ":, \t");
		bool err = false;
		if(ss.size() != 7) {
			err = true;
		} else {
			for(int i = 0; i < 7; ++i) {
				int val;
				if(utils::string_to_int(ss[i], val)) {
					if(val >= 0 && val <= 255) {
						id_.buff[i] = val;
					} else {
						err = true;
						break;
					}
				} else {
					err = true;
					break;
				}
			}
		}
		if(err) {
			return false;
		}
		return true;
	}


	bool start(const std::string& path, const std::string& brate) {
		using namespace r8c;

		// 開始
		if(!proto_.start(path)) {
			std::cerr << std::endl;
			std::cerr << "Can't open path: '" << path << "'" << std::endl;
			return false;
		}

		// コネクション
		if(!proto_.connection()) {
			proto_.end();
			std::cerr << std::endl;
			std::cerr << "Connection device error..." << std::endl;
			return false;
		}
		if(verbose_) {
			std::cout << "Connection OK." << std::endl;
		}

		// ボーレート変更
		int val;
		if(!utils::string_to_int(brate, val)) {
			std::cerr << std::endl;
			std::cerr << "Baud rate conversion error: '" << brate << std::endl;
			return false;
		}
		speed_t speed;
		switch(val) {
		case 9600:   speed = B9600;   break;
		case 19200:  speed = B19200;  break;
		case 38400:  speed = B38400;  break;
		case 57600:  speed = B57600;  break;
		case 115200: speed = B115200; break;
		default:
			proto_.end();
			std::cerr << "Baud rate error: " << brate << std::endl;
			return false;
		}

		if(!proto_.change_speed(speed)) {
			proto_.end();
			std::cerr << std::endl;
			std::cerr << "Change speed error: " << brate << std::endl;
			return false;
		}
		if(verbose_) {
			std::cout << "Change speed OK: " << brate << " [bps]" << std::endl;
		}

		// バージョンの取得
		ver_ = proto_.get_version();
		if(ver_.empty()) {
			proto_.end();
			std::cerr << std::endl;
			std::cerr << "Get version error..." << std::endl;
			return false;
		}
		if(verbose_) {
			std::cout << "Version: '" << ver_ << "'" << std::endl;
		}

		// ID チェック認証
		if(!proto_.id_inspection(id_)) {
			std::cerr << std::endl;
			std::cerr << "ID error: ";
			for(int i = 0; i < 7; ++i) {
				std::cerr << std::hex << std::setw(2) << std::uppercase << std::setfill('0')
						  << "0x" << static_cast<int>(id_.buff[i]) << ' ';
			}
			proto_.end();
			std::cerr << std::dec << std::endl;
			return false;
		}
		if(verbose_) {
			std::cout << "ID OK: ";
			for(int i = 0; i < 7; ++i) {
				std::cout << std::hex << std::setw(2) << std::uppercase << std::setfill('0')
						  << "0x" << static_cast<int>(id_.buff[i]) << ' ';
			}
			std::cout << std::endl;
		}

		set_.clear();

		return true;
	}


	bool read(uint32_t top, uint8_t* data) {
		if(!proto_.read_page(top, data)) {
			std::cerr << std::endl;
			std::cerr << "Read error: " << std::hex << std::setw(6)
					  << static_cast<int>(top) << " to " << static_cast<int>(top + 255)
					  << std::endl;
			return false;
		}
		return true;
	}


	bool erase_page(uint32_t top) {
		uint32_t area = 1024;
		if(top >= 0x8000) area = 4096;

		uint32_t adr = top & ~(area - 1);
		if(set_.find(adr) != set_.end()) {
			return true;
		}
		set_.insert(adr);

		// イレース
		if(!proto_.erase_page(top)) {
			std::cerr << std::endl;
			std::cerr << "Erase error: " << std::hex << std::setw(6)
					  << static_cast<int>(top) << " to " << static_cast<int>(top + 255)
					  << std::endl;
			return false;
		}
		return true;
	}


	bool write(uint32_t top, const uint8_t* data) {
		using namespace r8c;
		// ページ書き込み
		if(!proto_.write_page(top, data)) {
			std::cerr << std::endl;
   			std::cerr << "Write error: " << std::hex << std::setw(6)
					  << static_cast<int>(top) << " to " << static_cast<int>(top + 255)
					  << std::endl;
			return false;
		}
		return true;
   	}


	bool verify_page(uint32_t top, const uint8_t* data) {
		// ページ読み込み
		uint8_t tmp[256];
   		if(!proto_.read_page(top, tmp)) {
			std::cerr << std::endl;
   			std::cerr << "Read error: " << std::hex << std::setw(6)
					  << static_cast<int>(top) << " to " << static_cast<int>(top + 255)
					  << std::endl;
   			return false;
   		}

		uint32_t erc = 0;
		for(int i = 0; i < 256; ++i) {
			if(data[i] != tmp[i]) {
				if(erc == 0) {
					std::cerr << std::endl;
				}
   				std::cerr << boost::format("Verify error at 0x%06X: 0x%02X -> 0x%02X")
					% (top + i) % static_cast<uint32_t>(data[i]) % static_cast<uint32_t>(tmp[i]) << std::endl;
				++erc;
			}
		}
		return erc == 0;
	}

	void end() {
		proto_.end();
	}
};
```

## File: rs232c_io.hpp
```cpp
#pragma once
//=====================================================================//
/*!	@file
	@brief	RS232C 入出力クラス
    @author 平松邦仁 (hira@rvf-rc45.net)
	@copyright	Copyright (C) 2017 Kunihito Hiramatsu @n
				Released under the MIT license @n
				https://github.com/hirakuni45/R8C/blob/master/LICENSE
*/
//=====================================================================//
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <termios.h>
#include <unistd.h>
#include <limits.h>
#include <sys/ioctl.h>

#include <string>
#include <iostream>
#include <cstring>
#include <cstdio>

namespace utils {

	//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
	/*!
		@brief	RS232C I/O クラス
	*/
	//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
	class rs232c_io {
	public:

		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		/*!
			@brief	パリティの設定
		*/
		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		enum class parity {
			none,	///< パリティー無し
			even,	///< 偶数パリティ
			odd		///< 奇数パリティ
		};


		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		/*!
			@brief	キャラクター長
		*/
		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		enum class char_len {
			bits7,	///< ７ビット
			bits8	///< ８ビット
		};


		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		/*!
			@brief	ストップ・ビット長
		*/
		//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//
		enum class stop_len {
			one,	///< １ビット
			two		///< ２ビット
		};

	private:
		int    fd_;

		termios		attr_back_;
		termios		attr_;

		void close_() {
			tcsetattr(fd_, TCSANOW, &attr_back_);
			::close(fd_);
			fd_ = -1;
		}

	public:
		//-----------------------------------------------------------------//
		/*!
			@brief	コンストラクター
		*/
		//-----------------------------------------------------------------//
		rs232c_io() : fd_(-1) { }


		//-----------------------------------------------------------------//
		/*!
			@brief オープン
			@param[in]	path	シリアルポートパス
			@param[in]	brate	接続ボーレート
			@param[in]	clen	キャラクター長
			@param[in]	slen	ストップ・ビット長
			@param[in]	par		パリティ・ビット
			@param[in]	hc		ハードウェアー制御を有効にする場合「true」
			@return 正常なら「true」
		*/
		//-----------------------------------------------------------------//
		bool open(const std::string& path, speed_t brate,
			char_len clen = char_len::bits8, stop_len slen = stop_len::one,
			parity par = parity::none, bool hc = false) {

			int cpar = 0;
			int ipar = IGNBRK;
			switch(par) {
			case parity::none:
				cpar = 0;
				ipar = IGNPAR;
				break;
			case parity::even:
				cpar = PARENB;
				ipar = INPCK;
				break;
			case parity::odd:
				cpar = (PARENB | PARODD);
				ipar = INPCK;
				break;
			default:
				return false;
			}

			int bstop = 0;
			switch(slen) {
			case stop_len::one: bstop = 0;
				break;
			case stop_len::two: bstop = CSTOPB;
				break;
			default:
				return false;
			}

			int cbits = 0;
			switch(clen) {
			case char_len::bits8:
				cbits = CS8;
				break;
			case char_len::bits7:
				cbits = CS7;
				break;
			default:
				return false;
			}

			fd_ = ::open(path.c_str(), O_RDWR | O_NOCTTY | O_NDELAY);
			if(fd_ < 0) {
				return false;
			}

			if(tcgetattr(fd_, &attr_back_) == -1) {
				::close(fd_);
				fd_ = -1;
			}

			memset(&attr_, 0, sizeof(attr_));
			if(hc) {
				attr_.c_cflag = cbits | cpar | bstop | CRTSCTS | CREAD;
			} else {
				attr_.c_cflag = cbits | cpar | bstop | CLOCAL | CREAD;
			}
			attr_.c_iflag = ipar;
			attr_.c_oflag = 0;
			attr_.c_lflag = 0;
			attr_.c_cc[VMIN]  = 0;     // block untill n bytes are received
			attr_.c_cc[VTIME] = 1;     // block untill a timer expires (n * 100 mSec.)

			if(cfsetspeed(&attr_, brate) == -1) {
				close_();
				return false;
			}

			if(tcsetattr(fd_, TCSANOW, &attr_) == -1) {
				close_();
				return false;
			}

			int status;
			if(ioctl(fd_, TIOCMGET, &status) == -1) {
				close_();
				return false;
			}

			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	速度を変更
			@param[in]	brate 新しいボーレート
			@return 正常なら「true」
		*/
		//-----------------------------------------------------------------//
		bool change_speed(speed_t brate) {
			if(fd_ < 0) return false;

			if(cfsetspeed(&attr_, brate) == -1) {
				close_();
				return false;
			}
			if(tcsetattr(fd_, TCSANOW, &attr_) == -1) {
				close_();
				return false;
			}

			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	クローズ
			@return 正常なら「true」
		*/
		//-----------------------------------------------------------------//
		bool close() {
			if(fd_ < 0) return false;

			int status;
			if(ioctl(fd_, TIOCMGET, &status) == -1) {
				close_();
				return false;
			}

			status &= ~TIOCM_DTR;    /* turn off DTR */
			status &= ~TIOCM_RTS;    /* turn off RTS */

			if(ioctl(fd_, TIOCMSET, &status) == -1) {
				close_();
				return false;
			}

			close_();

			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	送信同期
			@return 正常なら「true」
		*/
		//-----------------------------------------------------------------//
		bool sync_send() const {
			if(fd_ < 0) return false;

			tcdrain(fd_);
			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	受信
			@param[out]	dst	受信データ転送先
			@param[in]	len	受信最大長さ
			@return 受信した長さ
		*/
		//-----------------------------------------------------------------//
		size_t recv(void* dst, size_t len) {
			if(fd_ < 0) return 0;

			return ::read(fd_, dst, len);
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	受信
			@param[out]	dst	受信データ転送先
			@param[in]	len	受信最大長さ
			@param[in]	tv	タイムアウト指定
			@return 受信した長さ
		*/
		//-----------------------------------------------------------------//
		size_t recv(void* dst, size_t len, const timeval& tv) {
			if(fd_ < 0) return 0;

			size_t total = 0;
			uint8_t* p = static_cast<uint8_t*>(dst);
			while(total < len) {
				fd_set fds;
				FD_ZERO(&fds);
				FD_SET(fd_, &fds);
				timeval t;
				t = tv;
				int ret = select(fd_ + 1, &fds, NULL, NULL, &t);
				if(ret == -1) {  // for error..
					break;
				} else if(ret > 0) {
					size_t rl = ::read(fd_, p, len - total);
					total += rl;
					p += rl;
				} else {
					break;
				}
			}
			return total;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	１バイト受信（タイムアウト）
			@param[in]	tv	タイムアウト指定
			@return 受信データ
		*/
		//-----------------------------------------------------------------//
		int recv(const timeval& tv) {
			uint8_t buff[1];
			if(recv(buff, 1, tv) != 1) {
				return EOF;
			}
			return static_cast<int>(buff[0]);
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	送信
			@param[in]	src	送信データ転送元
			@param[in]	len	送信長さ
			@return 送信した長さ
		*/
		//-----------------------------------------------------------------//
		size_t send(const void* src, size_t len) {
			if(fd_ < 0) return 0;

			return ::write(fd_, src, len);
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	送信
			@param[in]	ch	送信データ
			@return 成功なら「true」
		*/
		//-----------------------------------------------------------------//
		bool send(char ch) {
			if(fd_ < 0) return false;

			char buff[1];
			buff[0] = ch;
			return send(buff, 1) == 1;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	破棄
			@return 成功なら「true」
		*/
		//-----------------------------------------------------------------//
		bool flush()
		{
			return tcflush(fd_, TCIOFLUSH) == 0;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	DCD 信号の状態を取得
			@return DCD 信号
		*/
		//-----------------------------------------------------------------//
		bool get_DCD() const {
			if(fd_ < 0) return false;

			int status;
			if(ioctl(fd_, TIOCMGET, &status) == -1) {
				return false;
			}

			return status & TIOCM_CAR;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	CTS 信号の状態を取得
			@return CTS 信号
		*/
		//-----------------------------------------------------------------//
		bool get_CTS() const {
			if(fd_ < 0) return false;

			int status;
			if(ioctl(fd_, TIOCMGET, &status) == -1) {
				return false;
			}

			return status & TIOCM_CTS;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	DSR 信号の状態を取得
			@return DSR 信号
		*/
		//-----------------------------------------------------------------//
		bool get_DSR() const {
			if(fd_ < 0) return false;

			int status;

			if(ioctl(fd_, TIOCMGET, &status) == -1) {
				return false;
			}

			return status & TIOCM_DSR;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	TXD 端子のレベルを設定
			@param[in]	level	設定レベル
			@return 正常なら「true」
		*/
		//-----------------------------------------------------------------//
		bool set_TXD(bool level)
		{
			if(fd_ < 0) return false;

			int command;
			if(level) {
				command = TIOCCBRK;
			} else {
				command = TIOCSBRK;
			}
			return ioctl(fd_, command) != -1; 
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	DTR 信号の状態を設定
			@param[in]	ena 「flase」なら「０」
			@return 成功なら「true」
		*/
		//-----------------------------------------------------------------//
		bool enable_DTR(bool ena = true) {
			if(fd_ < 0) return false;

			int status;
			if(ioctl(fd_, TIOCMGET, &status) == -1) {
				return false;
			}

			if(ena) status |= TIOCM_DTR;
			else status &= ~TIOCM_DTR;

			if(ioctl(fd_, TIOCMSET, &status) == -1) {
				return false;
			}

			return true;
		}


		//-----------------------------------------------------------------//
		/*!
			@brief	RTS 信号の状態を設定
			@param[in]	ena 「flase」なら「０」
			@return 成功なら「true」
		*/
		//-----------------------------------------------------------------//
		bool enable_RTS(bool ena = true) {
			if(fd_ < 0) return false;

			int status;
			if(ioctl(fd_, TIOCMGET, &status) == -1) {
				return false;
			}

			if(ena) status |= TIOCM_RTS;
			else status &= ~TIOCM_RTS;

			if(ioctl(fd_, TIOCMSET, &status) == -1) {
				return false;
			}
			return true;
		}
	};
}
```

## File: main.cpp
```cpp
//=====================================================================//
/*!	@file
	@brief	Renesas R8C Series Programmer (Flash Writer)
    @author 平松邦仁 (hira@rvf-rc45.net)
	@copyright	Copyright (C) 2017, 2023 Kunihito Hiramatsu @n
				Released under the MIT license @n
				https://github.com/hirakuni45/R8C/blob/master/LICENSE
*/
//=====================================================================//
#include <iostream>
#include <iomanip>
#include <random>
#include <utility>
#include <cstdlib>
#include "r8c_prog.hpp"
#include "motsx_io.hpp"
#include "conf_in.hpp"
#include "area.hpp"
#include <boost/format.hpp>

namespace {

	const std::string version_ = "0.90";
	const std::string conf_file = "r8c_prog.conf";
	const uint32_t progress_num_ = 50;
	const char progress_cha_ = '#';

	utils::conf_in conf_in_;
	utils::motsx_io motsx_;

	const std::string get_current_path_(const std::string& exec)
	{
		std::string exec_path;
#ifdef WIN32
		{
			auto tmp = utils::sjis_to_utf8(exec);
			exec_path = utils::convert_delimiter(tmp, '\\', '/');
		}
#else
		exec_path = exec;
#endif
		std::string spch;
		std::string env;
		{
#ifdef WIN32
			auto tmp = sjis_to_utf8(getenv("PATH"));
			env = utils::convert_delimiter(tmp, '\\', '/');
			spch = ';';
#else
			env = getenv("PATH");
			spch = ':';
#endif
		}
		utils::strings ss = utils::split_text(env, spch);
		for(const auto& s : ss) {
			std::string path = s + '/' + utils::get_file_name(exec_path);
			if(utils::probe_file(path)) {
				return s;
			}
		}

		return std::string("");
	}


	struct page_t {
		uint32_t	n = 0;
		uint32_t	c = 0;
	};

	void progress_(const char* tag, uint32_t pageall, page_t& page)
	{
		std::string s;
		s += tag;
		std::cout << '\r';
		uint32_t pos = progress_num_ * page.n / pageall;
		for(uint32_t i = 0; i < progress_num_; ++i) {
			char ch = progress_cha_;
			if(pos < i) {
				ch = ' ';
			}
			s += ch;
		}
		s += (boost::format(" %3d %%") % ((page.n  + 1) * 100 / pageall)).str();
		std::cout << s << std::flush;
		page.c = pos;
	}


	struct options {
		bool verbose = false;

		std::string platform;

		std::string	inp_file;

		std::string	device;
		bool	dv = false;

		std::string	com_speed = "57600";
		bool	br = false;

		std::string com_path;
		std::string com_name;
		bool	dp = false;

		std::string id_val = "ff:ff:ff:ff:ff:ff:ff";
		bool	id = false;

		utils::areas area_val;
		bool	area = false;

		bool	read = false;
		bool	erase = false;
		bool	write = false;
		bool	verify = false;
		bool	device_list = false;
		bool	progress = false;
		bool	erase_data = false;
		bool	erase_rom = false;
		bool	help = false;


		bool set_area_(const std::string& s) {
			utils::strings ss = utils::split_text(s, ",");
			std::string t;
			if(ss.empty()) t = s;
			else if(ss.size() >= 1) t = ss[0];
			uint32_t org = 0;
			bool err = false;
			if(!utils::string_to_hex(t, org)) {
				err = true;
			}
			uint32_t end = org + 256;
			if(ss.size() >= 2) {
				if(!utils::string_to_hex(ss[1], end)) {
					err = true;
				}
			}
			if(err) {
				return false;
			}
			area_val.emplace_back(org, end);
			return true;
		}


		bool set_str(const std::string& t) {
			bool ok = true;
			if(br) {
				com_speed = t;
				br = false;
			} else if(dv) {
				device = t;
				dv = false;
			} else if(dp) {
				com_path = t;
				dp = false;
			} else if(id) {
				id_val = t;
				id = false;
			} else if(area) {
				if(!set_area_(t)) {
					ok = false;
				}
				area = false;
			} else {
				inp_file = t;
			}
			return ok;
		}


		bool area_check(uint32_t adr) {
			for(const auto& t : area_val) {
				if(t.org_ <= adr && adr <= t.end_) return true;
			}
			return false;
		}
	};


	void help_(const std::string& cmd)
	{
		using namespace std;

		std::string c = utils::get_file_base(cmd);

		cout << "Renesas R8C Series Programmer Version " << version_ << endl;
		cout << "Copyright (C) 2015, Hiramatsu Kunihito (hira@rvf-rc45.net)" << endl;
		cout << "usage:" << endl;
		cout << c << "[options] [mot file] ..." << endl;
		cout << endl;
		cout << "Options :" << endl;
		cout << "-d, --device=DEVICE\t\tSpecify device name" << endl;
		cout << "-e, --erase\t\t\tPerform a device erase to a minimum" << endl;
		cout << "    --erase-all, --erase-chip\tPerform rom and data flash erase" << endl;
		cout << "    --erase-rom\t\t\tPerform rom flash erase" << endl;
		cout << "    --erase-data\t\tPerform data flash erase" << endl;
		cout << "-i, --id=xx:xx:xx:xx:xx:xx:xx\tSpecify protect ID" << endl;
//		cout << "-p, --programmer=PROGRAMMER\tSpecify programmer name" << endl;
		cout << "-P, --port=PORT\t\t\tSpecify serial port" << endl;
//		cout << "-q\t\t\t\tQuell progress output" << endl;
		cout << "-a, --area=ORG,END\t\tSpecify read area" << endl;
		cout << "-r, --read\t\t\tPerform data read" << endl;
		cout << "-s, --speed=SPEED\t\tSpecify serial speed" << endl;
		cout << "-v, --verify\t\t\tPerform data verify" << endl;
		cout << "    --device-list\t\tDisplay device list" << endl;
//		cout << "    --programmer-list\t\tDisplay programmer list" << endl;
		cout << "-V, --verbose\t\t\tVerbose output" << endl;
		cout << "-w, --write\t\t\tPerform data write" << endl;
		cout << "    --progress\t\t\tdisplay Progress output" << endl;
		cout << "-h, --help\t\t\tDisplay this" << endl;
//		cout << "    --version\t\t\tDisplay version No." << endl;
	}


	bool erase_(const char* title, r8c_prog& prog, const utils::areas& as)
	{
		bool noerr = true;
		page_t page;
		for(const auto& t : as) {
   			if(!prog.erase_page(t.org_)) {
   				noerr = false;
				break;
			}
   			++page.n;
			if(prog.get_progress()) progress_(title, as.size(), page);
		}
		if(prog.get_progress()) std::cout << std::endl << std::flush;

		return noerr;
	}


	void dump_areas_(utils::motsx_io& motr, const utils::areas& as)
	{
		for(const auto& t : as) {
			uint32_t org = t.org_;
			uint32_t end = t.end_;
			while(org <= end) {
				const utils::motsx_io::array& a = motr.get_memory(org);
				uint32_t len = 256 - (org & 255);
				bool cr = false;
				if((org + len) > end) {
					len = end - org + 1;
					cr = true;
				}
				uint32_t ffcnt = 0;
				for(uint32_t i = 0; i < len; ++i) {
					if(a[(org + i) & 255] == 0xff) ++ffcnt;
				}
				if(ffcnt != len) {
////					dump_(org, len, &a[org & 255]);
					if(cr) std::cout << std::endl;
				}
				org |= 0xff;
				++org;
			}
		}
	}
}


int main(int argc, char* argv[])
{
	if(argc == 1) {
		help_(argv[0]);
		return 0;
	}

	options opts;

	// 設定ファイルの読み込み
	std::string conf_path;
	if(utils::probe_file(conf_file)) {  // カレントにあるか？
		conf_path = conf_file;
	} else {  // コマンド、カレントから読んでみる
		conf_path = get_current_path_(argv[0]) + '/' + conf_file;
	}

	if(conf_in_.load(conf_path)) {
		auto defa = conf_in_.get_default();
		opts.device = defa.device_;
#ifdef __CYGWIN__
		opts.platform = "Cygwin";
		opts.com_path = defa.port_win_;
		opts.com_speed = defa.speed_win_;
#endif
#ifdef __APPLE__
		opts.platform = "OS-X";
		opts.com_path = defa.port_osx_;
		opts.com_speed = defa.speed_osx_;
#endif
#ifdef __linux__
		opts.platform = "Linux";
		opts.com_path = defa.port_linux_;
		opts.com_speed = defa.speed_linux_;
#endif
		if(opts.com_path.empty()) {
			opts.com_path = defa.port_;
		}
		if(opts.com_speed.empty()) {
			opts.com_speed = defa.speed_;
		}
		opts.id_val = defa.id_;

#if 0
		if(0) {
			const utils::conf_in::programmer_t& pt = conf.get_programmer();
			std::cout << pt.comment_ << std::endl;
		}
		if(0) {
			const utils::conf_in::device_t& dt = conf.get_device();
			std::cout << dt.group_ << std::endl;
			std::cout << dt.ram_ << std::endl;
			std::cout << dt.rom_ << std::endl;
			std::cout << dt.data_ << std::endl;
			std::cout << dt.comment_ << std::endl;
			std::cout << dt.rom_area_.size() << std::endl;
			std::cout << dt.data_area_.size() << std::endl;
		}
#endif

	} else {
		std::cerr << "Configuration file can't load: '" << conf_path << '\'' << std::endl;
		return -1;
	}

   	// コマンドラインの解析
	bool opterr = false;
	for(int i = 1; i < argc; ++i) {
		const std::string p = argv[i];
		if(p[0] == '-') {
			if(p == "-V" || p == "--verbose") opts.verbose = true;
			else if(p == "-s") opts.br = true;
			else if(utils::string_strncmp(p, "--speed=", 8) == 0) { opts.com_speed = &p[8]; }
			else if(p == "-d") opts.dv = true;
			else if(utils::string_strncmp(p, "--device=", 9) == 0) { opts.device = &p[9]; }
			else if(p == "-P") opts.dp = true;
			else if(utils::string_strncmp(p, "--port=", 7) == 0) { opts.com_path = &p[7]; }
			else if(p == "-a") opts.area = true;
			else if(utils::string_strncmp(p, "--area=", 7) == 0) {
				if(!opts.set_area_(&p[7])) {
					opterr = true;
				}
			} else if(p == "-r" || p == "--read") opts.read = true;
			else if(p == "-e" || p == "--erase") opts.erase = true;
			else if(p == "-i") opts.id = true;
			else if(utils::string_strncmp(p, "--id=", 5) == 0) { opts.id_val = &p[5]; }
			else if(p == "-w" || p == "--write") opts.write = true;
			else if(p == "-v" || p == "--verify") opts.verify = true;
			else if(p == "--device-list") opts.device_list = true;
			else if(p == "--progress") opts.progress = true;
			else if(p == "--erase-rom") opts.erase_rom = true;
			else if(p == "--erase-data") opts.erase_data = true;
			else if(p == "--erase-all" || p == "--erase-chip") {
				opts.erase_rom = true;
				opts.erase_data = true;
			} else if(p == "-h" || p == "--help") opts.help = true;
			else {
				opterr = true;
			}
		} else {
			if(!opts.set_str(p)) {
				opterr = true;
			}
		}
		if(opterr) {
			std::cerr << "Option error: '" << p << "'" << std::endl;
			opts.help = true;
			opterr = false;
		}
	}
	if(opts.verbose) {
		std::cout << "# Platform: '" << opts.platform << '\'' << std::endl;
		std::cout << "# Configuration file path: '" << conf_path << '\'' << std::endl;
		std::cout << "# Device: '" << opts.device << '\'' << std::endl;
		std::cout << "# Serial port path: '" << opts.com_path << '\'' << std::endl;
		std::cout << "# Serial port speed: " << opts.com_speed << std::endl;
	}

	// HELP 表示
	if(opts.help || opts.com_path.empty() || (opts.inp_file.empty() && !opts.device_list)
///			&& opts.sequrity_set.empty() && !opts.sequrity_get && !opts.sequrity_release)
		|| opts.com_speed.empty() || opts.device.empty()) {
		if(opts.device.empty()) {
			std::cout << "Device name null." << std::endl;
		}
		if(opts.com_speed.empty()) {
			std::cout << "Serial speed none." << std::endl;
		}
		help_(argv[0]);
		return 0;
	}

	// デバイス・リスト表示
	if(opts.device_list) {
		for(const auto& s : conf_in_.get_device_list()) {
			std::cout << s << std::endl;
		}
	}

	// 入力ファイルの読み込み
	uint32_t pageall = 0;
	if(!opts.inp_file.empty()) {
		if(opts.verbose) {
			std::cout << "# Input file path: '" << opts.inp_file << '\'' << std::endl;
		}
		if(!motsx_.load(opts.inp_file)) {
			std::cerr << "Can't open input file: '" << opts.inp_file << "'" << std::endl;
			return -1;
		}
		pageall = motsx_.get_total_page();
		if(opts.verbose) {
			motsx_.list_area_map("# ");
		}
	}

    // Windwos系シリアル・ポート（COMx）の変換
    if(!opts.com_path.empty() && opts.com_path[0] != '/') {
		std::string s = utils::to_lower_text(opts.com_path);
        if(s.size() > 3 && s[0] == 'c' && s[1] == 'o' && s[2] == 'm') {
            int val;
            if(utils::string_to_int(&s[3], val)) {
                if(val >= 1 ) {
                    --val;
                    opts.com_name = opts.com_path;
                    opts.com_path = "/dev/ttyS" + (boost::format("%d") % val).str();
                }
            }
        }
		if(opts.verbose) {
			std::cout << "# Serial port alias: " << opts.com_name << " ---> " << opts.com_path << std::endl;
		}
    }
	if(opts.com_path.empty()) {
		std::cerr << "Serial port path not found." << std::endl;
		return -1;
	}

	if(opts.verbose) {
		std::cout << "# Serial port path: '" << opts.com_path << '\'' << std::endl;
	}
	int com_speed = 0;
	if(!utils::string_to_int(opts.com_speed, com_speed)) {
		std::cerr << "Serial speed conversion error: '" << opts.com_speed << '\'' << std::endl;
		return -1;		
	}

	if(!opts.erase && !opts.write && !opts.verify) return 0;
//		&& opts.sequrity_set.empty() && !opts.sequrity_get && !opts.sequrity_release) return 0;

	r8c_prog prog_(opts.verbose, opts.progress);

	if(opts.verbose) {
//		std::cout << "# Configuration file path: '" << conf_path << "'" << std::endl;
//		std::cout << "# Serial device file path: '" << opts.com_path << "'";
//		if(!opts.com_name.empty()) {
//			std::cout << "(" << opts.com_name << ")";
//		}
//		std::cout << std::endl;
//		std::cout << "# Serial device speed: " << opts.com_speed << " [bps]" << std::endl;
//		std::cout << "# Target device type: '" << opts.device << "'" << std::endl;

		std::cout << "# Device ID:";
		for(int i = 0; i < 7; ++i) {
			int v = static_cast<int>(prog_.get_id().buff[i]);
			std::cout << (boost::format(":%02X") % v).str();
		}
		std::cout << std::endl;
//		std::cout << "# Input file path: ";
//		if(opts.inp_file.empty()) {
//			std::cout << "-----" << std::endl;
//		} else {
//			std::cout << "'" << opts.inp_file << "'" << std::endl;
//		}
	}

	//=====================================
	if(!prog_.start(opts.com_path, opts.com_speed)) {
		return -1;
	}

	const utils::conf_in::device_t& devt = conf_in_.get_device();

	//===================================== リード
	if(opts.read) {
		if(opts.area_val.empty()) {  // エリア指定が無い場合
			if(devt.data_area_.size()) {
				const utils::areas& as = devt.data_area_;
				opts.area_val.emplace_back(as.front().org_, as.back().end_);
			}
			if(devt.rom_area_.size()) {
				const utils::areas& as = devt.rom_area_;
				opts.area_val.emplace_back(as.front().org_, as.back().end_);
			}
		}

		utils::motsx_io motr;
		uint32_t tpage = 0;
		const auto& as = opts.area_val;
		for(const auto& t : as) {
			tpage += ((t.end_ | 0xff) + 1 - (t.org_ & 0xffffff00)) >> 8;
		}
		if(tpage == 0) return 0;

		int err = 0;
		page_t page;
		for(const auto& t : as) {
			uint32_t sadr = t.org_;
			uint32_t eadr = t.end_;
			while(sadr <= eadr && err == 0) {
				uint8_t tmp[256];
				if(!prog_.read(sadr & 0xffffff00, tmp)) {
					++err;
					break;
				}
				uint32_t ofs = sadr & 255;
				motr.write(sadr, &tmp[ofs], 256 - ofs);
				sadr |= 255;
				++sadr;
				++page.n;
				if(prog_.get_progress()) {
					progress_("Read:   ", tpage, page);
				}
			}
		}
		if(prog_.get_progress()) {
			std::cout << std::endl << std::flush;
		}

		dump_areas_(motr, opts.area_val);
	}


	//===================================== イレース
	if(opts.erase_data || opts.erase_rom) {
		if(opts.erase_data) {
			if(!erase_("Erase-data: ", prog_, devt.data_area_)) {
				prog_.end();
				return -1;
			}
		}
		if(opts.erase_rom) {
			if(!erase_("Erase-rom:  ", prog_, devt.rom_area_)) {
				prog_.end();
				return -1;
			}
		}
	} else if(opts.erase) {  // 最適化消去（書き込むエリアのみ消去）
		auto areas = motsx_.create_area_map();

		page_t page;
		for(const auto& a : areas) {
			uint32_t adr = a.min_ & 0xffffff00;
			uint32_t len = 0;
			while(len < (a.max_ - a.min_ + 1)) {
				if(opts.progress) {
					progress_("Erase:  ", pageall, page);
				}
				if(!prog_.erase_page(adr)) {  // 256 バイト単位で消去要求を送る
					prog_.end();
					return -1;
				}
				adr += 256;
				len += 256;
				++page.n;
			}
		}
		if(opts.progress) {
			std::cout << std::endl << std::flush;
		}
	}


	//===================================== 書き込み
	if(opts.write) {
		auto areas = motsx_.create_area_map();

		page_t page;
		for(const auto& a : areas) {
			uint32_t adr = a.min_ & 0xffffff00;
			uint32_t len = 0;
			while(len < (a.max_ - a.min_ + 1)) {
				if(opts.progress) {
					progress_("Write:  ", pageall, page);
				}
				/// std::cout << boost::format("%08X to %08X") % adr % (adr + 255) << std::endl;
				auto mem = motsx_.get_memory(adr);
				if(!prog_.write(adr, &mem[0])) {
					prog_.end();
					return -1;
				}
				adr += 256;
				len += 256;
				++page.n;
			}
		}
		if(opts.progress) {
			std::cout << std::endl << std::flush;
		}
	}


	//===================================== verify
	if(opts.verify) {
		auto areas = motsx_.create_area_map();

		page_t page;
		for(const auto& a : areas) {
			uint32_t adr = a.min_ & 0xffffff00;
			uint32_t len = 0;
			while(len < (a.max_ - a.min_ + 1)) {
				if(opts.progress) {
					progress_("Verify: ", pageall, page);
				}
				/// std::cout << boost::format("%08X to %08X") % adr % (adr + 255) << std::endl;
				auto mem = motsx_.get_memory(adr);
				if(!prog_.verify_page(adr, &mem[0])) {
					prog_.end();
					return -1;
				}
				adr += 256;
				len += 256;
				++page.n;
			}
		}

		if(prog_.get_progress()) {
			std::cout << std::endl << std::flush;
		}
	}

	prog_.end();
}
```