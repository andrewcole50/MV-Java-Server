<?php

class Mvbase {

    public $item;
    public $file;
    public $record;
    public $conn;
    public $isError = false;
    public $message = array();
    public $AM = '';
    public $VM = '';
    public $SM = '';
    private $ipAddr = 'xxx.xxx.xxx.xxx';
    private $port = 55555;
    private $fields = array();
    private $mv = array();
    private $length;

    /**
     * Mvbase constructor.
     * @param $file
     * @param string $item
     * @param bool $lock
     */
    public function __construct($file, $item = 'false', $lock = false) {
        $this->file = strtoupper($file);
        $this->AM = chr(254);
        $this->AM = chr(253);
        $this->AM = chr(252);

        $this->conn = new PDO('mysql:host=;dbname=', 'user', 'pass');
        if ($file != 'sub') {
            //Get linked multivalue data
            $statement = $this->conn->prepare("
                SELECT 
                       f.field,
                       f.dict,
                       f.mv,
                       d.length
                FROM mv_fields f
                  LEFT JOIN mv_dictionaries d ON f.dictionary_id = d.id 
                WHERE d.mvName = :file"
            );
            $statement->execute(array(':file' => $this->file));
            $rs = $statement->fetchAll(PDO::FETCH_ASSOC);
            foreach ($rs as $field) {
                $this->length = $field['length'] + 1;
                $this->fields[$field['dict']] = $field['field'];
                $this->mv[$field['dict']] = $field['mv'];
            }

            if (strpos($item, $this->AM)) {
                $this->item = explode($this->AM, $item)[0];
                $this->record = $this->process($item, true);
            } else {
                $this->item = strtoupper($item);
                $this->record = $this->read($this->file, $this->item, $lock);
                if ($this->isError) {
                    $this->message[] = "Error: $this->item is not a valid number";
                }
            }
        }
    }

    /**
     * @param $array
     * @param bool $keyIncluded
     * @param bool $toMV
     * @return array|string
     */
    private function process($array, $keyIncluded = false, $toMV = false) {
        if ($toMV) {
            for ($i = 0; $i < count($array); $i++) {
                if (is_array($array[$i])) {
                    $array[$i] = implode($this->VM, $array[$i]);
                }
            }
            if ($keyIncluded) {
                array_shift($array);
            }
            $array = implode($this->AM, $array);
        } else {
            if ($keyIncluded) {
                $array = explode($this->AM, $array);
            } else {
                if ($array == '') $this->isError = true;
                $array = explode($this->AM, $this->item.$this->AM.$array);
            }
            foreach ($array as $key => $attribute) {
                $array[$key] = explode($this->VM, $attribute);
            }

            $current = count($array);
            while ($current < $this->length) {
                $array[] = null;
                $current = count($array);
            }

            for ($i = 0; $i < $current; $i++) {
                if (is_array($array[$i])) {
                    if (count($array[$i]) == 1) {
                        $array[$i] = $array[$i][0];
                    }
                }
            }
        }
        return $array;
    }

    /*************************************************************
     * Basic get,set,save methods
     ************************************************************
     * @param string $name
     * @return array|mixed
     */
    public function __get($name) {
        $attribute = $this->record[$this->fields[$name]];
        if ($this->mv[$name] == true && !is_array($attribute)) {
            $attribute = array($attribute);
        }
        return $attribute;
    }

    /**
     * @param $name
     * @param $data
     */
    public function __set($name, $data) {
        if ($data != $this->record[$this->fields[$name]]) {
            $this->record[$this->fields[$name]] = $data;
        }
    }

    /**
     * @param $file
     * @param $item
     * @param $lock
     * @return array|string
     */
    public function read($file, $item, $lock) {
        $command = ($lock ? 'JREADU' : 'JREAD');
        return $this->process($this->transmit(array('command' => $command, 'file' => $file, 'key' => $item)));
    }

    /**
     * @return bool|mixed|string
     */
    public function save() {
        $record = $this->process($this->record, true, true);
        $record = utf8_encode($record);

        $array = array('command' => 'JWRITE', 'file' => $this->file, 'key' => $this->item, 'data' => $record);

        $success = !$this->transmit($array);
        if ($success) $this->message[] = 'success';
        return $success;
    }

    /**
     * @return mixed|string
     */
    public function delete() {
        return $this->transmit(array('command' => 'DELETE', 'file' => $this->file, 'key' => $this->item));
    }

    /**
     * @param string $subName
     * @param array $args
     * @param int $length
     * @return mixed|string
     */
    public function goSub($subName, $args, $length) {

        foreach ($args as &$arg) {
            $arg = str_replace("~AM", $this->AM, $arg);
            $arg = str_replace("~VM", $this->VM, $arg);
            $arg = str_replace("~SM", $this->SM, $arg);
            $arg = utf8_encode($arg);
        }

        while (count($args) < $length) {
            $args[] = '';
        }

        return $this->transmit(array('command' => 'SUB', 'name' => $subName, 'arguments' => $args));
    }

    /**
     * @param string $file
     * @param string $item
     */
    public function release() {
        $this->transmit(array('command' => 'RELEASE', 'file' => $this->file, 'key' => $this->item));
    }

    /**
     * @param $query
     * @return mixed|string
     */
    public function query($query) {
        return $this->transmit(array('command' => 'QUERY', 'query' => $query));
    }

    /**
     * @param $data
     * @return mixed|string
     */
    private function transmit($data) {
        $response = '';
        $socket = socket_create(AF_INET, SOCK_STREAM, 0);
        if ($socket !== false) {
            if (socket_connect($socket, $this->ipAddr, $this->port)) {
                $request = json_encode($data) . chr(3);
                if (socket_write($socket, $request) !== false) {
                    socket_set_option($socket, SOL_SOCKET, SO_RCVTIMEO, array("sec" => 15, "usec" => 0));
                    while ($char = socket_read($socket, 1024, PHP_NORMAL_READ)) {
                        if (strpos($response, "\n") !== false) {
                            break;
                        } else {
                            $response .= $char;
                        }
                    }
                    $response = rtrim($response);
                    $response = rtrim($response, chr(3));
                }
            }
        }
        socket_close($socket);

        return $response;
    }

}
